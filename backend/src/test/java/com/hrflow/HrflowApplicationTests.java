package com.hrflow;

import com.hrflow.repo.LeavePolicyRepository;
import com.hrflow.repo.PublicHolidayRepository;
import com.hrflow.repo.WorkflowDefinitionRepository;
import com.hrflow.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the context loads, Flyway migrations apply on a real Postgres,
 * Hibernate validates the entity mapping, and the seeded reference data is
 * present (9 leave policies, 3 active workflows, 12 holidays).
 */
class HrflowApplicationTests extends AbstractPostgresTest {

    @Autowired LeavePolicyRepository leavePolicies;
    @Autowired WorkflowDefinitionRepository workflows;
    @Autowired PublicHolidayRepository holidays;

    @Test
    void contextLoadsAndReferenceDataSeeded() {
        assertThat(leavePolicies.count()).isEqualTo(9);   // 3 countries x 3 leave types
        assertThat(workflows.findByActiveTrue()).hasSize(3); // LEAVE, ONBOARDING, EXPENSE
        assertThat(holidays.count()).isEqualTo(12);        // 4 per country x 3
    }
}
