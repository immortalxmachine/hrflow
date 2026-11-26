package com.hrflow.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.LeaveType;
import com.hrflow.domain.Request;
import com.hrflow.domain.Role;
import com.hrflow.repo.RequestRepository;
import com.hrflow.support.AbstractPostgresTest;
import com.hrflow.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves SLA escalation: a leave request whose MANAGER step has breached its
 * 48h SLA is escalated to the step's escalation role (HR_ADMIN). After the
 * sweep, an HR admin -- who could NOT approve the manager step before -- is now
 * an eligible approver.
 */
@AutoConfigureMockMvc
class EscalationIntegrationTest extends AbstractPostgresTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fixtures;
    @Autowired ObjectMapper json;
    @Autowired RequestRepository requests;

    private LocalDate nextMonday() {
        LocalDate d = LocalDate.now().plusWeeks(2);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    void overdueManagerStepEscalatesToHrWhoCanThenApprove() throws Exception {
        Employee manager = fixtures.employee(Role.MANAGER, Country.IN, null);
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        Employee requester = fixtures.employee(Role.EMPLOYEE, Country.IN, manager);

        LocalDate start = nextMonday();
        String body = json.writeValueAsString(Map.of(
                "leaveType", LeaveType.ANNUAL, "startDate", start.toString(),
                "endDate", start.plusDays(1).toString()));
        String created = mvc.perform(post("/api/requests/leave")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(created).get("id").asLong();

        // Before escalation, HR cannot approve the MANAGER step.
        mvc.perform(post("/api/approvals/" + requestId + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        // Backdate the request so the 48h MANAGER SLA is breached.
        Request r = requests.findById(requestId).orElseThrow();
        r.setUpdatedAt(Instant.now().minus(100, ChronoUnit.HOURS));
        requests.save(r);

        // Run the sweep as HR -> exactly one escalation.
        mvc.perform(post("/api/admin/escalations/run")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalated").value(1));

        // Now HR (the escalation target) may approve the step -> advances to step 2.
        mvc.perform(post("/api/approvals/" + requestId + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepOrder").value(2));
    }
}
