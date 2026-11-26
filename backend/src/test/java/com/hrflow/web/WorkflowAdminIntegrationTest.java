package com.hrflow.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.Role;
import com.hrflow.support.AbstractPostgresTest;
import com.hrflow.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin workflow editing: creating a new active workflow for a type deactivates
 * the previously active one (one-active-per-type invariant), and non-contiguous
 * step orders are rejected with 422.
 */
@AutoConfigureMockMvc
class WorkflowAdminIntegrationTest extends AbstractPostgresTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fixtures;
    @Autowired ObjectMapper json;

    @Test
    void creatingActiveWorkflowDeactivatesPreviousOneForSameType() throws Exception {
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        String payload = """
                {"requestType":"LEAVE","name":"Revised Leave Flow","steps":[
                  {"stepOrder":1,"approverRole":"MANAGER","slaHours":24,"escalationRole":"HR_ADMIN"},
                  {"stepOrder":2,"approverRole":"HR_ADMIN","slaHours":48,"escalationRole":null}]}""";

        mvc.perform(post("/api/admin/workflows")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        // Exactly one active LEAVE workflow should remain.
        String list = mvc.perform(get("/api/admin/workflows")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        long activeLeave = 0;
        for (JsonNode wf : json.readTree(list)) {
            if (wf.get("requestType").asText().equals("LEAVE") && wf.get("active").asBoolean()) {
                activeLeave++;
            }
        }
        assertThat(activeLeave).isEqualTo(1);
    }

    @Test
    void nonContiguousStepOrdersRejected() throws Exception {
        Employee hr = fixtures.employee(Role.HR_ADMIN, Country.IN, null);
        String payload = """
                {"requestType":"EXPENSE","name":"Broken","steps":[
                  {"stepOrder":1,"approverRole":"MANAGER","slaHours":24,"escalationRole":null},
                  {"stepOrder":3,"approverRole":"HR_ADMIN","slaHours":48,"escalationRole":null}]}""";

        mvc.perform(post("/api/admin/workflows")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(hr))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity());
    }
}
