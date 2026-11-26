package com.hrflow.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** DTOs for the audit-log read API. */
public final class AuditDtos {

    private AuditDtos() { }

    public record AuditLogDto(
            Long id,
            Long actorId,
            String actorCode,
            String action,
            String entityType,
            Long entityId,
            JsonNode before,
            JsonNode after,
            Instant createdAt) { }
}
