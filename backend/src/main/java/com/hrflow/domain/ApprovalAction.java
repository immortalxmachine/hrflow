package com.hrflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A single approve/reject decision recorded against one step of a request.
 * Append-only: rows are inserted when an approver acts, never modified.
 */
@Entity
@Table(name = "approval_actions")
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column
    private String comment;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt = Instant.now();

    protected ApprovalAction() { }

    public ApprovalAction(Long requestId, int stepOrder, Long approverId,
                          Decision decision, String comment) {
        this.requestId = requestId;
        this.stepOrder = stepOrder;
        this.approverId = approverId;
        this.decision = decision;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public int getStepOrder() { return stepOrder; }
    public Long getApproverId() { return approverId; }
    public Decision getDecision() { return decision; }
    public String getComment() { return comment; }
    public Instant getActedAt() { return actedAt; }
}
