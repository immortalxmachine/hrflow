package com.hrflow.service;

import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.ImportBatch;
import com.hrflow.domain.ImportError;
import com.hrflow.domain.Role;
import com.hrflow.dto.ImportDtos.ImportErrorDto;
import com.hrflow.dto.ImportDtos.ImportResultDto;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.repo.ImportBatchRepository;
import com.hrflow.repo.ImportErrorRepository;
import com.hrflow.security.AuthenticatedUser;
import com.hrflow.security.SecurityUtils;
import com.hrflow.web.error.ApiExceptions.BusinessRuleException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bulk employee import from CSV with row-level validation.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>No silent drops</b>: every rejected row is written to import_errors
 *       with a human-readable reason.</li>
 *   <li><b>Idempotent on employee_code</b>: re-importing the same code updates
 *       the existing employee instead of creating a duplicate.</li>
 * </ul>
 *
 * <p>Expected header: {@code employee_code,full_name,email,country,role,department,manager_code}.
 * Imported accounts get a known synthetic default password (documented in the
 * README) so they can be used in the demo.
 */
@Service
public class ImportService {

    /** Default password for imported synthetic accounts (see README). */
    public static final String DEFAULT_PASSWORD = "Password123!";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> REQUIRED_HEADERS =
            List.of("employee_code", "full_name", "email", "country", "role");

    private final EmployeeRepository employees;
    private final ImportBatchRepository batches;
    private final ImportErrorRepository errors;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public ImportService(EmployeeRepository employees, ImportBatchRepository batches,
                         ImportErrorRepository errors, PasswordEncoder passwordEncoder,
                         AuditService audit) {
        this.employees = employees;
        this.batches = batches;
        this.errors = errors;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional
    public ImportResultDto importCsv(String filename, Reader reader) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        String actorCode = user == null ? "SYSTEM" : user.getUsername();

        ImportBatch batch = batches.save(new ImportBatch(filename, actorCode));
        String defaultHash = passwordEncoder.encode(DEFAULT_PASSWORD);

        // Resolve managers by code within DB + this batch as we go.
        Map<String, Employee> byCode = new HashMap<>();

        int total = 0, imported = 0, failed = 0;
        List<ImportErrorDto> errorDtos = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreEmptyLines(true).build();

        try (CSVParser p = format.parse(reader)) {
            validateHeader(p.getHeaderMap().keySet());

            for (CSVRecord record : p) {
                total++;
                int rowNo = total; // 1-based data row (header skipped)
                String raw = String.join(",", record.toList());

                List<String> rowErrors = new ArrayList<>();
                String code = get(record, "employee_code");
                String fullName = get(record, "full_name");
                String email = get(record, "email");
                String countryStr = get(record, "country");
                String roleStr = get(record, "role");
                String department = get(record, "department");
                String managerCode = get(record, "manager_code");

                if (isBlank(code)) rowErrors.add("employee_code is required");
                if (isBlank(fullName)) rowErrors.add("full_name is required");
                if (isBlank(email)) {
                    rowErrors.add("email is required");
                } else if (!EMAIL.matcher(email).matches()) {
                    rowErrors.add("email is malformed");
                }
                Country country = parseEnum(Country.class, countryStr, "country", rowErrors);
                Role role = parseEnum(Role.class, roleStr, "role", rowErrors);

                // Email uniqueness against a DIFFERENT employee code.
                if (!isBlank(email) && EMAIL.matcher(email).matches()) {
                    employees.findByEmail(email).ifPresent(existing -> {
                        if (!existing.getEmployeeCode().equalsIgnoreCase(code)) {
                            rowErrors.add("email already used by " + existing.getEmployeeCode());
                        }
                    });
                }

                // Manager resolution (optional). Must exist in DB or earlier in batch.
                Employee manager = null;
                if (!isBlank(managerCode)) {
                    manager = byCode.get(managerCode);
                    if (manager == null) {
                        manager = employees.findByEmployeeCode(managerCode).orElse(null);
                    }
                    if (manager == null) {
                        rowErrors.add("manager_code '" + managerCode + "' not found");
                    }
                }

                if (!rowErrors.isEmpty()) {
                    String reason = String.join("; ", rowErrors);
                    errors.save(new ImportError(batch.getId(), rowNo, raw, reason));
                    errorDtos.add(new ImportErrorDto(rowNo, reason, raw));
                    failed++;
                    continue;
                }

                // Upsert by employee_code (idempotent).
                Employee employee = employees.findByEmployeeCode(code).orElseGet(Employee::new);
                boolean isNew = employee.getId() == null;
                employee.setEmployeeCode(code);
                employee.setFullName(fullName);
                employee.setEmail(email);
                employee.setCountry(country);
                employee.setRole(role);
                employee.setDepartment(isBlank(department) ? null : department);
                employee.setManager(manager);
                if (isNew) {
                    employee.setPasswordHash(defaultHash);
                }
                Employee saved = employees.save(employee);
                byCode.put(code, saved);
                imported++;
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read CSV: " + e.getMessage());
        }

        batch.setTotalRows(total);
        batch.setImportedRows(imported);
        batch.setFailedRows(failed);
        batches.save(batch);

        audit.record("EMPLOYEE_IMPORT", "import_batch", batch.getId(), null, Map.of(
                "filename", filename, "total", total, "imported", imported, "failed", failed));

        return new ImportResultDto(batch.getId(), filename, total, imported, failed, errorDtos);
    }

    /** The recorded errors for a past import batch. */
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<ImportErrorDto> getErrors(Long batchId) {
        return errors.findByBatchIdOrderByRowNumberAsc(batchId).stream()
                .map(e -> new ImportErrorDto(e.getRowNumber(), e.getReason(), e.getRawRow()))
                .toList();
    }

    // --- helpers ---

    private void validateHeader(java.util.Set<String> headers) {
        for (String required : REQUIRED_HEADERS) {
            if (!headers.contains(required)) {
                throw new BusinessRuleException("CSV missing required column: " + required);
            }
        }
    }

    private String get(CSVRecord record, String column) {
        return record.isMapped(column) ? record.get(column) : null;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field, List<String> errors) {
        if (isBlank(value)) {
            errors.add(field + " is required");
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(field + " '" + value + "' is invalid");
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
