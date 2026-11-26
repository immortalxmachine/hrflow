package com.hrflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hrflow.domain.*;
import com.hrflow.dto.RequestDtos.ApprovalActionDto;
import com.hrflow.dto.RequestDtos.DecisionRequest;
import com.hrflow.dto.RequestDtos.RequestDto;
import com.hrflow.repo.*;
import com.hrflow.security.AuthenticatedUser;
import com.hrflow.security.SecurityUtils;
import com.hrflow.web.error.ApiExceptions.BusinessRuleException;
import com.hrflow.web.error.ApiExceptions.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a request through its workflow: validates the approver is eligible for
 * the current step, records the (append-only) decision, advances or terminates
 * the request, and on final leave approval deducts the balance and fires the
 * payroll webhook.
 */
@Service
public class ApprovalService {

    private final RequestRepository requests;
    private final WorkflowDefinitionRepository workflows;
    private final EmployeeRepository employees;
    private final ApprovalActionRepository actions;
    private final LeaveBalanceRepository balances;
    private final AuditService audit;
    private final WebhookService webhooks;
    private final RequestService requestService;

    public ApprovalService(RequestRepository requests, WorkflowDefinitionRepository workflows,
                           EmployeeRepository employees, ApprovalActionRepository actions,
                           LeaveBalanceRepository balances, AuditService audit,
                           WebhookService webhooks, RequestService requestService) {
        this.requests = requests;
        this.workflows = workflows;
        this.employees = employees;
        this.actions = actions;
        this.balances = balances;
        this.audit = audit;
        this.webhooks = webhooks;
        this.requestService = requestService;
    }

    /** Pending requests the current user is eligible to act on. */
    @Transactional(readOnly = true)
    public List<RequestDto> inbox() {
        AuthenticatedUser user = currentUser();
        return requests.findByStatus(RequestStatus.PENDING).stream()
                .filter(r -> isEligibleApprover(user, r))
                .map(requestService::toDto)
                .toList();
    }

    /** The full decision history of a request. */
    @Transactional(readOnly = true)
    public List<ApprovalActionDto> history(Long requestId) {
        return actions.findByRequestIdOrderByActedAtAsc(requestId).stream()
                .map(a -> new ApprovalActionDto(a.getStepOrder(), a.getApproverId(),
                        a.getDecision(), a.getComment(), a.getActedAt()))
                .toList();
    }

    /** Approve or reject the current step of a request. */
    @Transactional
    public RequestDto decide(Long requestId, DecisionRequest dec) {
        AuthenticatedUser user = currentUser();
        Request request = requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessRuleException("Request is not pending (status=" + request.getStatus() + ")");
        }

        WorkflowDefinition workflow = loadWorkflow(request.getWorkflowId());
        WorkflowStep step = currentStep(workflow, request.getCurrentStepOrder());

        // Authorization: must hold the step's effective role (and, for MANAGER
        // steps, actually be the requester's manager). Otherwise -> 403.
        if (!isEligibleApprover(user, request, workflow, step)) {
            throw new AccessDeniedException("You are not an eligible approver for this step");
        }

        RequestDto before = requestService.toDto(request);

        // Append-only record of the decision.
        actions.save(new ApprovalAction(request.getId(), step.getStepOrder(),
                user.getEmployeeId(), dec.decision(), dec.comment()));

        if (dec.decision() == Decision.REJECTED) {
            request.setStatus(RequestStatus.REJECTED);
        } else {
            int lastStep = workflow.getSteps().stream()
                    .mapToInt(WorkflowStep::getStepOrder).max().orElse(step.getStepOrder());
            if (step.getStepOrder() >= lastStep) {
                request.setStatus(RequestStatus.APPROVED);
                onFinalApproval(request);
            } else {
                request.setCurrentStepOrder(step.getStepOrder() + 1);
                request.setEscalated(false);     // escalation applies per step
                request.setEscalatedAt(null);
            }
        }
        request.setUpdatedAt(Instant.now());
        requests.save(request);

        RequestDto after = requestService.toDto(request);
        audit.record(dec.decision() == Decision.APPROVED ? "REQUEST_STEP_APPROVED" : "REQUEST_REJECTED",
                "request", request.getId(), before, after);
        return after;
    }

    // --- final approval side effects ---

    /** On the last approval of a leave request: deduct balance and notify payroll. */
    private void onFinalApproval(Request request) {
        if (request.getRequestType() != RequestType.LEAVE) {
            return;
        }
        JsonNode payload = requestService.toDto(request).payload();
        LeaveType leaveType = LeaveType.valueOf(payload.get("leaveType").asText());
        LocalDate startDate = LocalDate.parse(payload.get("startDate").asText());
        int workingDays = payload.get("workingDays").asInt();

        // Deduct from the requester's balance for the leave's year.
        balances.findByEmployeeIdAndLeaveTypeAndYear(request.getRequesterId(), leaveType, startDate.getYear())
                .ifPresent(bal -> {
                    bal.setUsedDays(bal.getUsedDays().add(BigDecimal.valueOf(workingDays)));
                    balances.save(bal);
                });

        Employee requester = employees.findById(request.getRequesterId()).orElse(null);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "LEAVE_APPROVED");
        event.put("requestId", request.getId());
        event.put("employeeCode", requester == null ? null : requester.getEmployeeCode());
        event.put("leaveType", leaveType.name());
        event.put("startDate", payload.get("startDate").asText());
        event.put("endDate", payload.get("endDate").asText());
        event.put("workingDays", workingDays);
        event.put("approvedAt", Instant.now().toString());

        webhooks.sendLeaveApproved(request.getId(), event);
    }

    // --- eligibility ---

    private boolean isEligibleApprover(AuthenticatedUser user, Request request) {
        WorkflowDefinition workflow = loadWorkflow(request.getWorkflowId());
        WorkflowStep step = currentStep(workflow, request.getCurrentStepOrder());
        return isEligibleApprover(user, request, workflow, step);
    }

    private boolean isEligibleApprover(AuthenticatedUser user, Request request,
                                       WorkflowDefinition workflow, WorkflowStep step) {
        Role required = request.isEscalated() && step.getEscalationRole() != null
                ? step.getEscalationRole() : step.getApproverRole();
        if (user.getRole() != required) {
            return false;
        }
        // A manager may only approve requests from their own direct reports.
        if (required == Role.MANAGER) {
            Employee requester = employees.findById(request.getRequesterId()).orElse(null);
            return requester != null && requester.getManager() != null
                    && requester.getManager().getId().equals(user.getEmployeeId());
        }
        return true; // HR_ADMIN / SYSTEM_ADMIN: any holder of the role may act
    }

    // --- helpers ---

    private WorkflowDefinition loadWorkflow(Long id) {
        return workflows.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private WorkflowStep currentStep(WorkflowDefinition workflow, int stepOrder) {
        return workflow.getSteps().stream()
                .filter(s -> s.getStepOrder() == stepOrder)
                .min(Comparator.comparingInt(WorkflowStep::getStepOrder))
                .orElseThrow(() -> new BusinessRuleException("No step " + stepOrder + " in workflow"));
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        return user;
    }
}
