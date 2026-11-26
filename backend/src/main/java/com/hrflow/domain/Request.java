package com.hrflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * A request travelling through an approval workflow (leave / onboarding /
 * expense). Type-specific fields are stored in {@code payload} as JSONB so the
 * engine stays generic; for LEAVE the payload holds the leave type and dates.
 */
@Entity
@Table(name = "requests")
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private RequestType requestType;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "current_step_order", nullable = false)
    private int currentStepOrder = 1;

    // Stored as Postgres JSONB; mapped as a JSON string we parse with Jackson.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Request() { }

    public Request(RequestType requestType, Long requesterId, Long workflowId, String payload) {
        this.requestType = requestType;
        this.requesterId = requesterId;
        this.workflowId = workflowId;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public RequestType getRequestType() { return requestType; }
    public Long getRequesterId() { return requesterId; }
    public Long getWorkflowId() { return workflowId; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public int getCurrentStepOrder() { return currentStepOrder; }
    public void setCurrentStepOrder(int currentStepOrder) { this.currentStepOrder = currentStepOrder; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
