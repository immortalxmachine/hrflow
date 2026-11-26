package com.hrflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.config.AppProperties;
import com.hrflow.domain.DeliveryStatus;
import com.hrflow.domain.WebhookDelivery;
import com.hrflow.repo.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Signs and delivers outbound webhooks to the payroll service, with retries and
 * a durable delivery log.
 *
 * <p>The payload is signed with HMAC-SHA256 over the exact JSON bytes using a
 * shared secret; the receiver recomputes the signature to verify authenticity.
 * Every attempt updates the {@link WebhookDelivery} row, so deliveries are
 * auditable and the final status (SUCCESS/FAILED) is queryable for metrics.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final String targetUrl;
    private final String secret;
    private final int maxAttempts;

    public WebhookService(WebhookDeliveryRepository deliveries, ObjectMapper mapper, AppProperties props) {
        this.deliveries = deliveries;
        this.mapper = mapper;
        this.restClient = RestClient.create();
        this.targetUrl = props.webhook().payrollUrl();
        this.secret = props.webhook().secret();
        this.maxAttempts = props.webhook().maxAttempts();
    }

    /**
     * Build, sign, persist and deliver a LEAVE_APPROVED webhook. Delivery errors
     * are swallowed (recorded as FAILED) so a payroll outage never rolls back an
     * approval that has already been granted.
     */
    @Transactional
    public WebhookDelivery sendLeaveApproved(Long requestId, Map<String, Object> event) {
        String body = toJson(event);
        String signature = hmacSha256(body, secret);

        WebhookDelivery delivery = new WebhookDelivery(
                requestId, "LEAVE_APPROVED", targetUrl, body, signature);
        delivery = deliveries.save(delivery);

        attemptDelivery(delivery, body, signature);
        return delivery;
    }

    /** Retry a single delivery that is still PENDING/FAILED (used by an admin re-drive). */
    @Transactional
    public WebhookDelivery redeliver(WebhookDelivery delivery) {
        attemptDelivery(delivery, delivery.getPayloadJson(), delivery.getSignature());
        return delivery;
    }

    private void attemptDelivery(WebhookDelivery delivery, String body, String signature) {
        for (int attempt = delivery.getAttemptCount() + 1; attempt <= maxAttempts; attempt++) {
            delivery.setAttemptCount(attempt);
            try {
                var response = restClient.post()
                        .uri(delivery.getTargetUrl())
                        .header("Content-Type", "application/json")
                        .header("X-HRFlow-Event", delivery.getEventType())
                        .header("X-HRFlow-Signature", signature)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();

                delivery.setLastResponseCode(response.getStatusCode().value());
                if (response.getStatusCode().is2xxSuccessful()) {
                    delivery.setStatus(DeliveryStatus.SUCCESS);
                    delivery.setLastError(null);
                    delivery.setUpdatedAt(Instant.now());
                    deliveries.save(delivery);
                    return; // delivered
                }
                delivery.setLastError("Non-2xx: " + response.getStatusCode());
            } catch (Exception ex) {
                // Network error, 4xx/5xx, timeout, etc. Record and retry.
                delivery.setLastError(truncate(ex.getMessage()));
                log.warn("Webhook delivery attempt {} failed for delivery {}: {}",
                        attempt, delivery.getId(), ex.getMessage());
            }
        }
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setUpdatedAt(Instant.now());
        deliveries.save(delivery);
    }

    /** Compute the hex HMAC-SHA256 of {@code data} under {@code key}. */
    public static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC", e);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialise webhook payload", e);
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 290 ? s.substring(0, 290) : s;
    }
}
