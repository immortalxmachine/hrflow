package com.hrflow.service;

import com.hrflow.domain.Request;
import com.hrflow.domain.RequestStatus;
import com.hrflow.domain.WorkflowDefinition;
import com.hrflow.domain.WorkflowStep;
import com.hrflow.repo.RequestRepository;
import com.hrflow.repo.WorkflowDefinitionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Escalates requests whose current step has breached its SLA.
 *
 * <p>A step is overdue when it has been the current step for longer than its
 * {@code slaHours} (measured from the request's last update) and it defines an
 * {@code escalationRole}. Escalation is recorded on the request (not on the
 * shared workflow definition) and flips who may approve the step to the
 * escalation role. In production this method would be driven by a scheduler;
 * here it is invoked via an admin endpoint so the behaviour is deterministic
 * and testable.
 */
@Service
public class EscalationService {

    private final RequestRepository requests;
    private final WorkflowDefinitionRepository workflows;
    private final AuditService audit;

    public EscalationService(RequestRepository requests, WorkflowDefinitionRepository workflows,
                             AuditService audit) {
        this.requests = requests;
        this.workflows = workflows;
        this.audit = audit;
    }

    /**
     * Scan pending, not-yet-escalated requests and escalate those past SLA.
     *
     * @return the number of requests escalated in this sweep
     */
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional
    public int escalateOverdue() {
        Instant now = Instant.now();
        int escalated = 0;

        for (Request request : requests.findByStatusAndEscalatedFalse(RequestStatus.PENDING)) {
            Optional<WorkflowStep> stepOpt = currentStep(request);
            if (stepOpt.isEmpty()) {
                continue;
            }
            WorkflowStep step = stepOpt.get();
            if (step.getEscalationRole() == null) {
                continue; // nothing to escalate to
            }
            Duration elapsed = Duration.between(request.getUpdatedAt(), now);
            if (elapsed.toHours() >= step.getSlaHours()) {
                request.setEscalated(true);
                request.setEscalatedAt(now);
                request.setUpdatedAt(now);
                requests.save(request);
                audit.record("REQUEST_ESCALATED", "request", request.getId(), null, Map.of(
                        "stepOrder", step.getStepOrder(),
                        "fromRole", step.getApproverRole().name(),
                        "toRole", step.getEscalationRole().name(),
                        "slaHours", step.getSlaHours()));
                escalated++;
            }
        }
        return escalated;
    }

    private Optional<WorkflowStep> currentStep(Request request) {
        return workflows.findById(request.getWorkflowId())
                .flatMap(wf -> wf.getSteps().stream()
                        .filter(s -> s.getStepOrder() == request.getCurrentStepOrder())
                        .findFirst());
    }
}
