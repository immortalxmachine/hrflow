package com.hrflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.domain.AuditLog;
import com.hrflow.repo.AuditLogRepository;
import com.hrflow.security.AuthenticatedUser;
import com.hrflow.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit entries. Every config change and approval decision
 * calls this. Snapshots are stored as JSON so any entity shape can be captured.
 *
 * <p>Writes run in a REQUIRED transaction (joining the caller's), so an audit
 * row is committed together with the change it records -- they succeed or fail
 * as one unit.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLog;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository auditLog, ObjectMapper mapper) {
        this.auditLog = auditLog;
        this.mapper = mapper;
    }

    /** Record an action attributed to the current authenticated user (if any). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Long entityId,
                       Object before, Object after) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        Long actorId = user == null ? null : user.getEmployeeId();
        String actorCode = user == null ? "SYSTEM" : user.getUsername();
        auditLog.save(new AuditLog(actorId, actorCode, action, entityType, entityId,
                toJson(before), toJson(after)));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Never fail the business operation because audit serialisation failed;
            // store a safe fallback instead.
            return "{\"_serializationError\":\"" + e.getOriginalMessage() + "\"}";
        }
    }
}
