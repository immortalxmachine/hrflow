package com.hrflow.seed;

import com.hrflow.config.AppProperties;
import com.hrflow.domain.*;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.repo.LeaveBalanceRepository;
import com.hrflow.repo.LeavePolicyRepository;
import com.hrflow.service.ImportService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the employee population from the <b>real, public IBM HR Analytics
 * Employee Attrition dataset</b> when {@code hrflow.seed-on-startup=true} and
 * the employees table is empty.
 *
 * <p>Source: Kaggle {@code pavansubhasht/ibm-hr-analytics-attrition-dataset}
 * ({@code WA_Fn-UseC_-HR-Employee-Attrition.csv}, 1,470 rows), licensed
 * DbCL-1.0. It is a public, fully anonymised teaching dataset created by IBM
 * data scientists — it contains <b>no real individuals</b>.
 *
 * <p><b>Real, from the dataset (verbatim):</b> Department, JobRole, JobLevel,
 * MonthlyIncome, Age, Gender, MaritalStatus, and the row's EmployeeNumber (which
 * drives the stable {@code employee_code} and the neutral {@code "Employee NNNN"}
 * display label — the dataset has no names).
 *
 * <p><b>Operational, assigned by this seeder (the dataset does not carry it):</b>
 * the login email (on the reserved, clearly-non-real {@code .invalid} domain),
 * the shared demo password, the HRFlow access {@link Role} (derived from the
 * real JobRole/Department — see {@link #deriveRole}), the leave-rule
 * {@link Country} (round-robined only to exercise the multi-jurisdiction engine),
 * and the manager / HR-business-partner wiring (the dataset has no org chart).
 * None of this invents personal attributes of a real person; it is the plumbing
 * the workflow engine needs to run on top of the real rows.
 *
 * <p>Generation is deterministic (rows ordered by EmployeeNumber, fixed
 * round-robin assignment) so metrics are reproducible.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Classpath location of the real dataset (see {@code sample-data/ibm-hr-attrition/}). */
    static final String DATASET_RESOURCE = "data/WA_Fn-UseC_-HR-Employee-Attrition.csv";

    /** Leave-policy jurisdictions the engine is configured for (operational routing only). */
    private static final Country[] COUNTRIES = {Country.IN, Country.AE, Country.DE};

    /** Clearly-non-real domain for login identities (RFC 2606 reserved TLD). */
    private static final String EMAIL_DOMAIN = "sample.hrflow.invalid";

    private final EmployeeRepository employees;
    private final LeavePolicyRepository policies;
    private final LeaveBalanceRepository balances;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DataSeeder(EmployeeRepository employees, LeavePolicyRepository policies,
                      LeaveBalanceRepository balances, PasswordEncoder passwordEncoder,
                      AppProperties props) {
        this.employees = employees;
        this.policies = policies;
        this.balances = balances;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seedOnStartup()) {
            return;
        }
        if (employees.count() > 0) {
            log.info("Seed skipped: {} employees already present", employees.count());
            return;
        }
        seed();
    }

    /** Public so it can be invoked directly from a seed CLI / tests. */
    @Transactional
    public int seed() {
        List<Row> rows = loadDataset();
        String hash = passwordEncoder.encode(ImportService.DEFAULT_PASSWORD);
        int year = LocalDate.now().getYear();

        // One global system administrator. This single account is operational
        // (platform owner), not a dataset row.
        Employee sysadmin = employees.save(newEmployee(
                "SYSADMIN", "System Administrator", "sysadmin@" + EMAIL_DOMAIN,
                Country.IN, Role.SYSTEM_ADMIN, "IT", null, hash));
        seedBalances(sysadmin, year);

        // --- Pass 1: approvers (MANAGER / HR_ADMIN) first, so employees can
        // reference them. Managers and HR are grouped by their operational
        // country so manager/requester pairs stay within one jurisdiction. ---
        Map<Country, List<Employee>> managersByCountry = new LinkedHashMap<>();
        List<Employee> hrPool = new ArrayList<>();
        for (Country c : COUNTRIES) {
            managersByCountry.put(c, new ArrayList<>());
        }

        for (Row r : rows) {
            if (r.role == Role.MANAGER) {
                Employee mgr = employees.save(toEmployee(r, null, hash));
                managersByCountry.get(r.country).add(mgr);
            } else if (r.role == Role.HR_ADMIN) {
                hrPool.add(employees.save(toEmployee(r, null, hash)));
            }
        }

        // --- Pass 2: regular employees, wired to a same-country manager (fall
        // back to any manager) and an HR business partner. ---
        List<Employee> allManagers = managersByCountry.values().stream()
                .flatMap(List::stream).toList();
        Map<Country, Integer> mgrCursor = new LinkedHashMap<>();
        int hrCursor = 0, empCursor = 0;

        for (Row r : rows) {
            if (r.role != Role.EMPLOYEE) {
                continue;
            }
            List<Employee> pool = managersByCountry.get(r.country);
            Employee manager;
            if (!pool.isEmpty()) {
                int i = mgrCursor.merge(r.country, 1, Integer::sum) - 1;
                manager = pool.get(i % pool.size());
            } else {
                manager = allManagers.get(empCursor % allManagers.size());
            }
            empCursor++;

            Employee emp = toEmployee(r, manager, hash);
            if (!hrPool.isEmpty()) {
                emp.setHrbp(hrPool.get(hrCursor++ % hrPool.size()));
            }
            seedBalances(employees.save(emp), year);
        }

        // Give the approver accounts an HR business partner and balances too.
        for (Employee mgr : allManagers) {
            if (!hrPool.isEmpty()) {
                mgr.setHrbp(hrPool.get(hrCursor++ % hrPool.size()));
            }
            seedBalances(mgr, year);
        }
        for (Employee hr : hrPool) {
            seedBalances(hr, year);
        }

        long total = employees.count();
        log.info("Seeded {} employees from the IBM HR Attrition dataset "
                        + "({} managers, {} HR admins, 1 system admin) across {} leave jurisdictions",
                total, allManagers.size(), hrPool.size(), COUNTRIES.length);
        return (int) total;
    }

    // --- dataset loading ---

    private List<Row> loadDataset() {
        String csv;
        try {
            byte[] bytes = new ClassPathResource(DATASET_RESOURCE).getInputStream().readAllBytes();
            csv = new String(bytes, StandardCharsets.UTF_8);
            if (!csv.isEmpty() && csv.charAt(0) == '﻿') {
                csv = csv.substring(1); // strip UTF-8 BOM so the first header parses cleanly
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read seed dataset on classpath: " + DATASET_RESOURCE, e);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreEmptyLines(true).build();

        List<Row> rows = new ArrayList<>();
        try (CSVParser p = format.parse(new StringReader(csv))) {
            for (CSVRecord rec : p) {
                Row r = new Row();
                r.employeeNumber = Integer.parseInt(rec.get("EmployeeNumber"));
                r.department = rec.get("Department");
                r.jobRole = rec.get("JobRole");
                r.jobLevel = parseIntOrNull(rec.get("JobLevel"));
                r.monthlyIncome = parseIntOrNull(rec.get("MonthlyIncome"));
                r.age = parseIntOrNull(rec.get("Age"));
                r.gender = rec.get("Gender");
                r.maritalStatus = rec.get("MaritalStatus");
                rows.add(r);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse seed dataset: " + DATASET_RESOURCE, e);
        }

        // Deterministic order + operational country assignment (round-robin so
        // all three jurisdictions are populated). Country is NOT in the dataset.
        rows.sort(Comparator.comparingInt(a -> a.employeeNumber));
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            r.country = COUNTRIES[i % COUNTRIES.length];
            r.role = deriveRole(r.department, r.jobRole);
        }
        return rows;
    }

    /**
     * Map the real IBM JobRole / Department onto an HRFlow access role:
     * the dataset's "Manager" job role becomes {@link Role#MANAGER}; Human
     * Resources staff in the HR department become {@link Role#HR_ADMIN}
     * (the engine's HR approvers); everyone else is a plain {@link Role#EMPLOYEE}.
     */
    private Role deriveRole(String department, String jobRole) {
        if ("Manager".equals(jobRole)) {
            return Role.MANAGER;
        }
        if ("Human Resources".equals(department) && "Human Resources".equals(jobRole)) {
            return Role.HR_ADMIN;
        }
        return Role.EMPLOYEE;
    }

    // --- helpers ---

    private Employee toEmployee(Row r, Employee manager, String hash) {
        String kind = switch (r.role) {
            case MANAGER -> "MG";
            case HR_ADMIN -> "HR";
            case SYSTEM_ADMIN -> "SY";
            case EMPLOYEE -> "EM";
        };
        String code = String.format("%s-%s-%04d", r.country.name(), kind, r.employeeNumber);
        String label = String.format("Employee %04d", r.employeeNumber);
        String email = String.format("emp-%04d@%s", r.employeeNumber, EMAIL_DOMAIN);

        Employee e = newEmployee(code, label, email, r.country, r.role, r.department, manager, hash);
        e.setAge(r.age);
        e.setGender(r.gender);
        e.setJobRole(r.jobRole);
        e.setJobLevel(r.jobLevel);
        e.setMonthlyIncome(r.monthlyIncome);
        e.setMaritalStatus(r.maritalStatus);
        return e;
    }

    private Employee newEmployee(String code, String fullName, String email, Country country,
                                 Role role, String department, Employee manager, String hash) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFullName(fullName);
        e.setEmail(email);
        e.setCountry(country);
        e.setRole(role);
        e.setDepartment(department);
        e.setManager(manager);
        e.setPasswordHash(hash);
        e.setActive(true);
        return e;
    }

    /** Create ANNUAL/SICK/CASUAL balances for an employee from their country policy. */
    private void seedBalances(Employee e, int year) {
        for (LeaveType type : LeaveType.values()) {
            policies.findByCountryAndLeaveType(e.getCountry(), type).ifPresent(policy ->
                    balances.save(new LeaveBalance(e.getId(), type, year,
                            BigDecimal.valueOf(policy.getAnnualEntitlement()), BigDecimal.ZERO)));
        }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A parsed dataset row plus the operational fields this seeder assigns. */
    private static final class Row {
        int employeeNumber;
        String department;
        String jobRole;
        Integer jobLevel;
        Integer monthlyIncome;
        Integer age;
        String gender;
        String maritalStatus;
        Country country;   // operational (assigned)
        Role role;         // derived from department/jobRole
    }
}
