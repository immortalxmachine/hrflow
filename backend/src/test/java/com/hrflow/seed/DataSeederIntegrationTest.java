package com.hrflow.seed;

import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.Role;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.repo.LeaveBalanceRepository;
import com.hrflow.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the startup seed against the real IBM HR Attrition dataset on the
 * classpath. The seeder is invoked directly (its {@code run()} is gated off in
 * tests); the surrounding {@code @Transactional} rolls everything back.
 *
 * <p>These assertions pin the real counts derived from the dataset's own
 * JobRole/Department columns (1,470 rows) and confirm that the real per-row
 * attributes are persisted and the manager hierarchy is wired so the approval
 * engine has eligible approvers.
 */
class DataSeederIntegrationTest extends AbstractPostgresTest {

    @Autowired DataSeeder seeder;
    @Autowired EmployeeRepository employees;
    @Autowired LeaveBalanceRepository balances;

    private static final Set<String> REAL_DEPARTMENTS =
            Set.of("Sales", "Research & Development", "Human Resources");

    @Test
    void seedsRealIbmPopulationWithDerivedRolesAndRealAttributes() {
        int total = seeder.seed();

        // 1,470 dataset rows + 1 operational system admin.
        assertThat(total).isEqualTo(1471);
        assertThat(employees.count()).isEqualTo(1471);

        // Roles derived from the dataset's own JobRole/Department columns.
        assertThat(employees.findByRole(Role.MANAGER)).hasSize(102);
        assertThat(employees.findByRole(Role.HR_ADMIN)).hasSize(52);
        assertThat(employees.findByRole(Role.SYSTEM_ADMIN)).hasSize(1);
        assertThat(employees.findByRole(Role.EMPLOYEE)).hasSize(1316);

        // Real attributes are carried verbatim onto the seeded rows.
        List<Employee> emps = employees.findByRole(Role.EMPLOYEE);
        assertThat(emps).allSatisfy(e -> {
            assertThat(e.getDepartment()).isIn(REAL_DEPARTMENTS);
            assertThat(e.getAge()).isBetween(18, 60);          // real dataset range
            assertThat(e.getGender()).isIn("Male", "Female");
            assertThat(e.getJobLevel()).isBetween(1, 5);
            assertThat(e.getMonthlyIncome()).isPositive();
            assertThat(e.getJobRole()).isNotBlank();
            // Operational: neutral label + clearly-non-real login domain.
            assertThat(e.getFullName()).startsWith("Employee ");
            assertThat(e.getEmail()).endsWith("@sample.hrflow.invalid");
            assertThat(e.getCountry()).isIn(Country.IN, Country.AE, Country.DE);
        });

        // Every regular employee is wired to a MANAGER-role manager, so the
        // workflow engine always has an eligible level-1 approver.
        assertThat(emps).allSatisfy(e -> {
            assertThat(e.getManager()).isNotNull();
            assertThat(e.getManager().getRole()).isEqualTo(Role.MANAGER);
        });

        // Leave balances were seeded (3 leave types x every employee).
        assertThat(balances.count()).isEqualTo(1471L * 3);
    }
}
