package com.hrflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * Append-only audit record. Written for every configuration change and every
 * approval decision, capturing who did what with before/after JSON snapshots.
 *
 * <p>There is intentionally no setter and no update/delete endpoint: once
 * written, an audit row is immutable.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;            // null for SYSTEM actions

    @Column(name = "actor_code")
    private String actorCode;        // e.g. "EMP0001" or "SYSTEM"

    @Column(nullable = false)
    private String action;           // e.g. WORKFLOW_UPDATED, REQUEST_APPROVED

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private String afterJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() { }

    public AuditLog(Long actorId, String actorCode, String action,
                    String entityType, Long entityId, String beforeJson, String afterJson) {
        this.actorId = actorId;
        this.actorCode = actorCode;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorCode() { return actorCode; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public Instant getCreatedAt() { return createdAt; }
}
