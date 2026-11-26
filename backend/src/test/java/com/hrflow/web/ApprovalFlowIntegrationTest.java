package com.hrflow.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.LeaveType;
import com.hrflow.domain.Role;
import com.hrflow.repo.AuditLogRepository;
import com.hrflow.repo.LeaveBalanceRepository;
import com.hrflow.repo.WebhookDeliveryRepository;
import com.hrflow.support.AbstractPostgresTest;
import com.hrflow.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end leave flow through the seeded two-step LEAVE workflow
 * (MANAGER -> HR_ADMIN): raise, approve step 1 as the direct manager, approve
 * step 2 as HR, then assert the balance was deducted, the payroll webhook was
 * attempted, and the decisions were audited.
 */
@AutoConfigureMockMvc
class ApprovalFlowIntegrationTest extends AbstractPostgresTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fixtures;
    @Autowired ObjectMapper json;
    @Autowired LeaveBalanceRepository balances;
    @Autowired WebhookDeliveryRepository webhooks;
    @Autowired AuditLogRepository auditLog;

    private LocalDate nextMonday(int plusWeeks) {
        LocalDate d = LocalDate.now().plusWeeks(plusWeeks);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    void fullLeaveApprovalDeductsBalanceFiresWebhookAndAudits() throws Exception {
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        Employee manager = fixtures.employee(Role.MANAGER, Country.IN, null);
        Employee requester = fixtures.employee(Role.EMPLOYEE, Country.IN, manager);

        LocalDate start = nextMonday(2);
        LocalDate end = start.plusDays(2); // Mon..Wed = 3 working days

        // 1. Raise the leave request.
        String body = json.writeValueAsString(Map.of(
                "leaveType", LeaveType.ANNUAL, "startDate", start.toString(), "endDate", end.toString()));
        String created = mvc.perform(post("/api/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentApproverRole").value("MANAGER"))
                .andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(created).get("id").asLong();

        // 2. Manager (the requester's own manager) approves step 1 -> advances to HR.
        mvc.perform(post("/api/approvals/" + requestId + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentStepOrder").value(2))
                .andExpect(jsonPath("$.currentApproverRole").value("HR_ADMIN"));

        // 3. HR approves step 2 -> final approval.
        mvc.perform(post("/api/approvals/" + requestId + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 4. Balance deducted by the 3 working days.
        var balance = balances.findByEmployeeIdAndLeaveTypeAndYear(
                requester.getId(), LeaveType.ANNUAL, start.getYear()).orElseThrow();
        assertThat(balance.getUsedDays().intValueExact()).isEqualTo(3);

        // 5. A payroll webhook delivery was recorded (payroll is down in tests, so it
        //    exhausts retries and ends FAILED -- but the approval still succeeded).
        assertThat(webhooks.findAll()).anyMatch(w -> w.getRequestId().equals(requestId));

        // 6. Two decisions were audited plus the creation.
        assertThat(auditLog.count()).isGreaterThanOrEqualTo(3);

        // 7. History has both approvals.
        mvc.perform(get("/api/approvals/" + requestId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void managerOfAnotherTeamCannotApprove() throws Exception {
        Employee manager = fixtures.employee(Role.MANAGER, Country.IN, null);
        Employee otherManager = fixtures.employee(Role.MANAGER, Country.IN, null);
        Employee requester = fixtures.employee(Role.EMPLOYEE, Country.IN, manager);

        LocalDate start = nextMonday(2);
        String body = json.writeValueAsString(Map.of(
                "leaveType", LeaveType.ANNUAL, "startDate", start.toString(),
                "endDate", start.plusDays(1).toString()));
        String created = mvc.perform(post("/api/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(created).get("id").asLong();

        // A manager who is NOT this requester's manager must be forbidden.
        mvc.perform(post("/api/approvals/" + requestId + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(otherManager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }
}
