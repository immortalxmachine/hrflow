package com.hrflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.domain.AuditLog;
import com.hrflow.dto.AuditDtos.AuditLogDto;
import com.hrflow.repo.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, paginated and filterable access to the audit log. Restricted to
 * HR/system admins. There is deliberately no method to modify or delete entries.
 */
@Service
public class AuditQueryService {

    private final AuditLogRepository auditLog;
    private final ObjectMapper mapper;

    public AuditQueryService(AuditLogRepository auditLog, ObjectMapper mapper) {
        this.auditLog = auditLog;
        this.mapper = mapper;
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(String action, String entityType, Long actorId, Pageable pageable) {
        return auditLog.search(action, entityType, actorId, pageable).map(this::toDto);
    }

    private AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActorId(), a.getActorCode(), a.getAction(),
                a.getEntityType(), a.getEntityId(), readJson(a.getBeforeJson()),
                readJson(a.getAfterJson()), a.getCreatedAt());
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
