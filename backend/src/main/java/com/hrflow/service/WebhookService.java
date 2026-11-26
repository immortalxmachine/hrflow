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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    // Use the JDK HTTP client (not RestClient) so the body is sent as the EXACT
    // bytes we signed. A String/JSON body goes through a message converter that
    // can re-encode it (quoting/escaping), which would break HMAC verification.
    private final HttpClient httpClient;
    private final String targetUrl;
    private final String secret;
    private final int maxAttempts;

    public WebhookService(WebhookDeliveryRepository deliveries, ObjectMapper mapper, AppProperties props) {
        this.deliveries = deliveries;
        this.mapper = mapper;
        // Force HTTP/1.1: the JDK client defaults to HTTP/2, whose cleartext (h2c)
        // upgrade some HTTP/1.1-only servers (e.g. uvicorn/h11) reject outright.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
        HttpRequest request = HttpRequest.newBuilder(URI.create(delivery.getTargetUrl()))
                .header("Content-Type", "application/json")
                .header("X-HRFlow-Event", delivery.getEventType())
                .header("X-HRFlow-Signature", signature)
                // ofString sends exactly these bytes -- the same ones we signed.
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();

        for (int attempt = delivery.getAttemptCount() + 1; attempt <= maxAttempts; attempt++) {
            delivery.setAttemptCount(attempt);
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                delivery.setLastResponseCode(code);
                if (code >= 200 && code < 300) {
                    delivery.setStatus(DeliveryStatus.SUCCESS);
                    delivery.setLastError(null);
                    delivery.setUpdatedAt(Instant.now());
                    deliveries.save(delivery);
                    return; // delivered
                }
                delivery.setLastError(truncate("HTTP " + code + ": " + response.body()));
            } catch (Exception ex) {
                // Network error, timeout, interruption, etc. Record and retry.
                delivery.setLastError(truncate(ex.getMessage()));
                log.warn("Webhook delivery attempt {} failed for delivery {}: {}",
                        attempt, delivery.getId(), ex.getMessage());
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
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
