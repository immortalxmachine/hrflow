package com.hrflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * Durable log of an outbound webhook (e.g. notifying payroll of an approved
 * leave). Tracks the signed payload, attempt count and final status so
 * deliveries are auditable and retryable.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(nullable = false)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_response_code")
    private Integer lastResponseCode;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WebhookDelivery() { }

    public WebhookDelivery(Long requestId, String eventType, String targetUrl,
                           String payloadJson, String signature) {
        this.requestId = requestId;
        this.eventType = eventType;
        this.targetUrl = targetUrl;
        this.payloadJson = payloadJson;
        this.signature = signature;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public String getEventType() { return eventType; }
    public String getTargetUrl() { return targetUrl; }
    public String getPayloadJson() { return payloadJson; }
    public String getSignature() { return signature; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Integer getLastResponseCode() { return lastResponseCode; }
    public void setLastResponseCode(Integer lastResponseCode) { this.lastResponseCode = lastResponseCode; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
