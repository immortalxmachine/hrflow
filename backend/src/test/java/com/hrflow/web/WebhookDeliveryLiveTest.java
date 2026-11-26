package com.hrflow.web;

import com.hrflow.domain.DeliveryStatus;
import com.hrflow.domain.WebhookDelivery;
import com.hrflow.service.WebhookService;
import com.hrflow.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIVE end-to-end webhook test against a running payroll-mock on :8001.
 * Disabled unless {@code -Dhrflow.live.webhook=true} is passed, so normal CI
 * does not require the external service. Produces the real "delivery after
 * injected failures" metric recorded in metrics.json.
 *
 * Run: start the mock (uvicorn app:app --port 8001), then
 *   mvnw test -Dtest=WebhookDeliveryLiveTest -Dhrflow.live.webhook=true
 */
@EnabledIfSystemProperty(named = "hrflow.live.webhook", matches = "true")
class WebhookDeliveryLiveTest extends AbstractPostgresTest {

    @Autowired WebhookService webhooks;
    private final RestClient http = RestClient.create();

    private Map<String, Object> event(long id) {
        return Map.of("event", "LEAVE_APPROVED", "requestId", id, "workingDays", 3);
    }

    @Test
    void retriesThenSucceedsAfterTwoInjectedFailures() {
        http.post().uri("http://localhost:8001/control/reset").retrieve().toBodilessEntity();
        // Fail the next 2 attempts; the 3rd (= maxAttempts) should succeed.
        http.post().uri("http://localhost:8001/control/fail-next/2").retrieve().toBodilessEntity();

        WebhookDelivery delivery = webhooks.sendLeaveApproved(null, event(1001L));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(delivery.getAttemptCount()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    void marksFailedWhenAllAttemptsExhausted() {
        http.post().uri("http://localhost:8001/control/reset").retrieve().toBodilessEntity();
        // Fail more times than maxAttempts(3) -> delivery exhausts and is FAILED.
        http.post().uri("http://localhost:8001/control/fail-next/5").retrieve().toBodilessEntity();

        WebhookDelivery delivery = webhooks.sendLeaveApproved(null, event(1002L));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(3); // capped at maxAttempts
    }
}
