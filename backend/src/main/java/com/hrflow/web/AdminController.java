package com.hrflow.web;

import com.hrflow.dto.AuditDtos.AuditLogDto;
import com.hrflow.service.AuditQueryService;
import com.hrflow.service.EscalationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints: the paginated/filterable audit log and a manual trigger for
 * the SLA escalation sweep. Both are role-restricted in the service layer.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditQueryService auditQuery;
    private final EscalationService escalation;

    public AdminController(AuditQueryService auditQuery, EscalationService escalation) {
        this.auditQuery = auditQuery;
        this.escalation = escalation;
    }

    /** Filterable audit log. Any filter may be omitted. */
    @GetMapping("/audit")
    public Page<AuditLogDto> audit(@RequestParam(required = false) String action,
                                   @RequestParam(required = false) String entityType,
                                   @RequestParam(required = false) Long actorId,
                                   @PageableDefault(size = 25) Pageable pageable) {
        return auditQuery.search(action, entityType, actorId, pageable);
    }

    /** Run the escalation sweep now; returns how many requests were escalated. */
    @PostMapping("/escalations/run")
    public Map<String, Integer> runEscalation() {
        return Map.of("escalated", escalation.escalateOverdue());
    }
}
