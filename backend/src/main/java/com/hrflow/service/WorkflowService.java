package com.hrflow.service;

import com.hrflow.domain.WorkflowDefinition;
import com.hrflow.domain.WorkflowStep;
import com.hrflow.dto.WorkflowDtos.CreateWorkflowRequest;
import com.hrflow.dto.WorkflowDtos.StepDto;
import com.hrflow.dto.WorkflowDtos.UpdateWorkflowRequest;
import com.hrflow.dto.WorkflowDtos.WorkflowDto;
import com.hrflow.repo.WorkflowDefinitionRepository;
import com.hrflow.web.error.ApiExceptions.BusinessRuleException;
import com.hrflow.web.error.ApiExceptions.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Admin API for configurable approval workflows. The approval chains are DATA:
 * creating or editing these rows changes how requests are approved, with no
 * code change. Every mutation is role-restricted and audited.
 */
@Service
public class WorkflowService {

    private final WorkflowDefinitionRepository workflows;
    private final AuditService audit;

    public WorkflowService(WorkflowDefinitionRepository workflows, AuditService audit) {
        this.workflows = workflows;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<WorkflowDto> list() {
        return workflows.findAll().stream().map(WorkflowDto::from).toList();
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public WorkflowDto get(Long id) {
        return WorkflowDto.from(load(id));
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional
    public WorkflowDto create(CreateWorkflowRequest req) {
        validateSteps(req.steps());

        // Enforce "one active workflow per request type": deactivate the current
        // active one first, flushing so the partial unique index is free before
        // we insert the new active row.
        workflows.findByRequestTypeAndActiveTrue(req.requestType()).ifPresent(existing -> {
            existing.setActive(false);
            workflows.saveAndFlush(existing);
            audit.record("WORKFLOW_DEACTIVATED", "workflow_definition", existing.getId(),
                    null, WorkflowDto.from(existing));
        });

        WorkflowDefinition def = new WorkflowDefinition(req.requestType(), req.name());
        addSteps(def, req.steps());
        WorkflowDefinition saved = workflows.save(def);

        audit.record("WORKFLOW_CREATED", "workflow_definition", saved.getId(),
                null, WorkflowDto.from(saved));
        return WorkflowDto.from(saved);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    @Transactional
    public WorkflowDto update(Long id, UpdateWorkflowRequest req) {
        validateSteps(req.steps());
        WorkflowDefinition def = load(id);
        WorkflowDto before = WorkflowDto.from(def); // snapshot before mutation

        // If re-activating this workflow, deactivate any other active one for the
        // same type first (keeps the one-active-per-type invariant).
        if (req.active() && !def.isActive()) {
            workflows.findByRequestTypeAndActiveTrue(def.getRequestType())
                    .filter(other -> !other.getId().equals(def.getId()))
                    .ifPresent(other -> {
                        other.setActive(false);
                        workflows.saveAndFlush(other);
                        audit.record("WORKFLOW_DEACTIVATED", "workflow_definition", other.getId(),
                                null, WorkflowDto.from(other));
                    });
        }

        def.setName(req.name());
        def.setActive(req.active());
        def.getSteps().clear();        // orphanRemoval deletes the old steps
        addSteps(def, req.steps());
        WorkflowDefinition saved = workflows.save(def);

        audit.record("WORKFLOW_UPDATED", "workflow_definition", saved.getId(),
                before, WorkflowDto.from(saved));
        return WorkflowDto.from(saved);
    }

    // --- helpers ---

    private WorkflowDefinition load(Long id) {
        return workflows.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private void addSteps(WorkflowDefinition def, List<StepDto> steps) {
        steps.stream()
                .sorted(Comparator.comparingInt(StepDto::stepOrder))
                .forEach(s -> def.addStep(new WorkflowStep(
                        s.stepOrder(), s.approverRole(), s.slaHours(), s.escalationRole())));
    }

    /** Step orders must be 1..n contiguous with no duplicates. */
    private void validateSteps(List<StepDto> steps) {
        List<Integer> orders = steps.stream().map(StepDto::stepOrder).sorted().toList();
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i) != i + 1) {
                throw new BusinessRuleException(
                        "Step orders must be contiguous starting at 1 (got " + orders + ")");
            }
        }
    }
}
