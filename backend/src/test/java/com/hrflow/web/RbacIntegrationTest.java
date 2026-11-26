package com.hrflow.web;

import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.Role;
import com.hrflow.support.AbstractPostgresTest;
import com.hrflow.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the role-based access control: admin endpoints reject non-admins with
 * 403 and unauthenticated callers with 401, while admins succeed. Authorization
 * is enforced in the service layer (@PreAuthorize) and surfaced as HTTP status.
 */
@AutoConfigureMockMvc
class RbacIntegrationTest extends AbstractPostgresTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fixtures;

    @Test
    void employeeIsForbiddenFromListingWorkflows() throws Exception {
        Employee emp = fixtures.employee(Role.EMPLOYEE, Country.IN, null);
        mvc.perform(get("/api/admin/workflows").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(emp)))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrAdminCanListWorkflows() throws Exception {
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        mvc.perform(get("/api/admin/workflows").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedGetsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/workflows"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeIsForbiddenFromAuditLog() throws Exception {
        Employee emp = fixtures.employee(Role.EMPLOYEE, Country.AE, null);
        mvc.perform(get("/api/admin/audit").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(emp)))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeIsForbiddenFromRunningEscalation() throws Exception {
        Employee emp = fixtures.employee(Role.EMPLOYEE, Country.DE, null);
        mvc.perform(post("/api/admin/escalations/run").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(emp)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTokenGetsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/workflows").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
