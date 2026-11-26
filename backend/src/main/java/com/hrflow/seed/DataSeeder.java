package com.hrflow.seed;

import com.hrflow.config.AppProperties;
import com.hrflow.domain.*;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.repo.LeaveBalanceRepository;
import com.hrflow.repo.LeavePolicyRepository;
import com.hrflow.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds ~200 SYNTHETIC employees across India, UAE and Germany when
 * {@code hrflow.seed-on-startup=true} and the employees table is empty.
 *
 * <p>All data is fabricated (names from fixed lists, @synthetic.hrflow.test
 * emails) and every account uses the documented default password. Generation is
 * deterministic (fixed RNG seed) so metrics are reproducible.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[] FIRST = {
            "Aarav", "Diya", "Kabir", "Mohammed", "Fatima", "Omar", "Layla", "Lukas",
            "Anna", "Jonas", "Priya", "Rohan", "Sara", "Yusuf", "Mia", "Felix", "Noor", "Hana"};
    private static final String[] LAST = {
            "Sharma", "Patel", "Khan", "Al-Farsi", "Müller", "Schmidt", "Reddy", "Nair",
            "Hassan", "Weber", "Fischer", "Iyer", "Rahman", "Becker", "Kaur", "Wagner"};
    private static final Country[] COUNTRIES = {Country.IN, Country.AE, Country.DE};

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
        Random rnd = new Random(42); // deterministic
        String hash = passwordEncoder.encode(ImportService.DEFAULT_PASSWORD);
        int year = LocalDate.now().getYear();

        // One global system administrator.
        Employee sysadmin = employees.save(newEmployee(
                "SYSADMIN", "System Admin", Country.IN, Role.SYSTEM_ADMIN, "IT", null, hash));
        seedBalances(sysadmin, year);

        int perCountryHr = 2, perCountryMgr = 6, perCountryEmp = 58;
        int counter = 0;

        for (Country country : COUNTRIES) {
            List<Employee> hrAdmins = new ArrayList<>();
            List<Employee> managers = new ArrayList<>();

            for (int i = 1; i <= perCountryHr; i++) {
                Employee hr = employees.save(newEmployee(
                        code(country, "HR", ++counter), name(rnd), country, Role.HR_ADMIN, "People", null, hash));
                seedBalances(hr, year);
                hrAdmins.add(hr);
            }
            for (int i = 1; i <= perCountryMgr; i++) {
                Employee mgr = employees.save(newEmployee(
                        code(country, "MG", ++counter), name(rnd), country, Role.MANAGER, dept(rnd),
                        hrAdmins.get(rnd.nextInt(hrAdmins.size())), hash));
                mgr.setHrbp(hrAdmins.get(rnd.nextInt(hrAdmins.size())));
                seedBalances(mgr, year);
                managers.add(mgr);
            }
            for (int i = 1; i <= perCountryEmp; i++) {
                Employee mgr = managers.get(rnd.nextInt(managers.size()));
                Employee emp = employees.save(newEmployee(
                        code(country, "EM", ++counter), name(rnd), country, Role.EMPLOYEE, dept(rnd), mgr, hash));
                emp.setHrbp(hrAdmins.get(rnd.nextInt(hrAdmins.size())));
                seedBalances(emp, year);
            }
        }

        long total = employees.count();
        log.info("Seeded {} synthetic employees across {} countries", total, COUNTRIES.length);
        return (int) total;
    }

    // --- helpers ---

    private Employee newEmployee(String code, String fullName, Country country, Role role,
                                 String department, Employee manager, String hash) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFullName(fullName);
        e.setEmail(code.toLowerCase() + "@synthetic.hrflow.test");
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

    private String code(Country c, String kind, int n) {
        return String.format("%s-%s-%04d", c.name(), kind, n);
    }

    private String name(Random rnd) {
        return FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
    }

    private String dept(Random rnd) {
        String[] depts = {"Engineering", "Sales", "Finance", "Operations", "Marketing"};
        return depts[rnd.nextInt(depts.length)];
    }
}
