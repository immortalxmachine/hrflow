package com.hrflow.web;

import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.Role;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.support.AbstractPostgresTest;
import com.hrflow.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the CSV import with a deliberately dirty file: 3 valid rows and 5
 * invalid ones (missing name, bad email, bad country, bad role, unknown
 * manager). Asserts no silent drops (5 errors recorded) and idempotency on
 * employee_code (re-importing does not create duplicates).
 */
@AutoConfigureMockMvc
class ImportIntegrationTest extends AbstractPostgresTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fixtures;
    @Autowired EmployeeRepository employees;

    // 8 data rows: E100/E101/E107 valid; the rest each break one rule.
    private static final String DIRTY_CSV = """
            employee_code,full_name,email,country,role,department,manager_code
            E100,Alice Stone,alice@synthetic.hrflow.test,IN,EMPLOYEE,Engineering,
            E101,Bob Lane,bob@synthetic.hrflow.test,AE,MANAGER,Sales,
            E102,,carol@synthetic.hrflow.test,IN,EMPLOYEE,Finance,
            E103,Dan Ray,not-an-email,DE,EMPLOYEE,Ops,
            E104,Eve Kim,eve@synthetic.hrflow.test,XX,EMPLOYEE,Ops,
            E105,Finn Oak,finn@synthetic.hrflow.test,IN,WIZARD,Ops,
            E106,Gina Fox,gina@synthetic.hrflow.test,IN,EMPLOYEE,Ops,E999
            E107,Hank Poe,hank@synthetic.hrflow.test,IN,EMPLOYEE,Ops,E101
            """;

    @Test
    void dirtyCsvImportsValidRowsAndRecordsEveryError() throws Exception {
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        long before = employees.count();

        perform(hr)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(8))
                .andExpect(jsonPath("$.importedRows").value(3))
                .andExpect(jsonPath("$.failedRows").value(5))
                .andExpect(jsonPath("$.errors.length()").value(5));

        // 3 new employees persisted (plus the HR fixture that already existed).
        org.assertj.core.api.Assertions.assertThat(employees.count()).isEqualTo(before + 3);
    }

    @Test
    void reimportIsIdempotentOnEmployeeCode() throws Exception {
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        long before = employees.count();

        perform(hr).andExpect(status().isOk());
        perform(hr).andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(3)); // upserts, not new rows

        // Still only 3 distinct employees were added across two identical imports.
        org.assertj.core.api.Assertions.assertThat(employees.count()).isEqualTo(before + 3);
    }

    private org.springframework.test.web.servlet.ResultActions perform(Employee hr) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dirty.csv", "text/csv", DIRTY_CSV.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/admin/employees/import").file(file)
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr)));
    }
}
