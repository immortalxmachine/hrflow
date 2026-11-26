package com.hrflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrflow.domain.*;
import com.hrflow.dto.RequestDtos.CreateGenericRequest;
import com.hrflow.dto.RequestDtos.CreateLeaveRequest;
import com.hrflow.dto.RequestDtos.RequestDto;
import com.hrflow.repo.*;
import com.hrflow.rules.LeaveRuleEngine;
import com.hrflow.rules.LeaveValidationResult;
import com.hrflow.security.AuthenticatedUser;
import com.hrflow.security.SecurityUtils;
import com.hrflow.web.error.ApiExceptions.BusinessRuleException;
import com.hrflow.web.error.ApiExceptions.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Raises requests and exposes them. Leave requests are validated against the
 * requester's country policy by the {@link LeaveRuleEngine} before they enter
 * the workflow; onboarding/expense requests carry a free-form JSON payload.
 */
@Service
public class RequestService {

    private final EmployeeRepository employees;
    private final WorkflowDefinitionRepository workflows;
    private final RequestRepository requests;
    private final LeavePolicyRepository policies;
    private final PublicHolidayRepository holidays;
    private final LeaveBalanceRepository balances;
    private final LeaveRuleEngine ruleEngine;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public RequestService(EmployeeRepository employees, WorkflowDefinitionRepository workflows,
                          RequestRepository requests, LeavePolicyRepository policies,
                          PublicHolidayRepository holidays, LeaveBalanceRepository balances,
                          LeaveRuleEngine ruleEngine, AuditService audit, ObjectMapper mapper) {
        this.employees = employees;
        this.workflows = workflows;
        this.requests = requests;
        this.policies = policies;
        this.holidays = holidays;
        this.balances = balances;
        this.ruleEngine = ruleEngine;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** Raise a leave request for the currently authenticated employee. */
    @Transactional
    public RequestDto createLeave(CreateLeaveRequest req) {
        Employee requester = currentEmployee();

        LeavePolicy policy = policies.findByCountryAndLeaveType(requester.getCountry(), req.leaveType())
                .orElseThrow(() -> new BusinessRuleException(
                        "No " + req.leaveType() + " policy for country " + requester.getCountry()));

        // Build the holiday set for the requester's country within the range.
        Set<LocalDate> holidaySet = holidays
                .findByCountryAndHolidayDateBetween(requester.getCountry(), req.startDate(), req.endDate())
                .stream().map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());

        LeaveBalance balance = getOrCreateBalance(requester.getId(), req.leaveType(),
                req.startDate().getYear(), policy);

        LeaveValidationResult result = ruleEngine.validate(
                req.startDate(), req.endDate(), policy, holidaySet, balance.remaining(), LocalDate.now());
        if (!result.valid()) {
            throw new BusinessRuleException(String.join("; ", result.violations()));
        }

        WorkflowDefinition workflow = activeWorkflow(RequestType.LEAVE);

        // Payload captures the leave specifics; workingDays is used later when the
        // balance is deducted on final approval.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leaveType", req.leaveType().name());
        payload.put("startDate", req.startDate().toString());
        payload.put("endDate", req.endDate().toString());
        payload.put("workingDays", result.workingDays());

        Request saved = requests.save(new Request(
                RequestType.LEAVE, requester.getId(), workflow.getId(), writeJson(payload)));
        audit.record("REQUEST_CREATED", "request", saved.getId(), null, Map.of(
                "requestType", "LEAVE", "requesterId", requester.getId(), "payload", payload));
        return toDto(saved);
    }

    /** Raise an onboarding or expense request with a free-form payload. */
    @Transactional
    public RequestDto createGeneric(CreateGenericRequest req) {
        if (req.requestType() == RequestType.LEAVE) {
            throw new BusinessRuleException("Use the /leave endpoint for leave requests");
        }
        Employee requester = currentEmployee();
        WorkflowDefinition workflow = activeWorkflow(req.requestType());

        Request saved = requests.save(new Request(
                req.requestType(), requester.getId(), workflow.getId(), writeJson(req.payload())));
        audit.record("REQUEST_CREATED", "request", saved.getId(), null, Map.of(
                "requestType", req.requestType().name(), "requesterId", requester.getId(),
                "payload", req.payload()));
        return toDto(saved);
    }

    /** The current user's own requests. */
    @Transactional(readOnly = true)
    public Page<RequestDto> myRequests(Pageable pageable) {
        Long me = currentUser().getEmployeeId();
        return requests.findByRequesterId(me, pageable).map(this::toDto);
    }

    /** View a single request; only the requester, HR_ADMIN or SYSTEM_ADMIN may. */
    @Transactional(readOnly = true)
    public RequestDto get(Long id) {
        Request r = requests.findById(id)
                .orElseThrow(() -> new NotFoundException("Request not found: " + id));
        AuthenticatedUser user = currentUser();
        boolean privileged = user.getRole() == Role.HR_ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!privileged && !r.getRequesterId().equals(user.getEmployeeId())) {
            throw new AccessDeniedException("Not allowed to view this request");
        }
        return toDto(r);
    }

    // --- helpers ---

    private WorkflowDefinition activeWorkflow(RequestType type) {
        return workflows.findByRequestTypeAndActiveTrue(type)
                .orElseThrow(() -> new BusinessRuleException("No active workflow for " + type));
    }

    private LeaveBalance getOrCreateBalance(Long employeeId, LeaveType type, int year, LeavePolicy policy) {
        return balances.findByEmployeeIdAndLeaveTypeAndYear(employeeId, type, year)
                .orElseGet(() -> balances.save(new LeaveBalance(employeeId, type, year,
                        java.math.BigDecimal.valueOf(policy.getAnnualEntitlement()),
                        java.math.BigDecimal.ZERO)));
    }

    private Employee currentEmployee() {
        Long id = currentUser().getEmployeeId();
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = SecurityUtils.currentUser();
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        return user;
    }

    /** Map an entity to its API view, resolving the escalation-aware current role. */
    RequestDto toDto(Request r) {
        Role currentRole = resolveCurrentApproverRole(r);
        return new RequestDto(r.getId(), r.getRequestType(), r.getRequesterId(), r.getStatus(),
                r.getCurrentStepOrder(), currentRole, r.isEscalated(), readJson(r.getPayload()),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    /** The role that must act on the current step now (escalation target if escalated). */
    private Role resolveCurrentApproverRole(Request r) {
        if (r.getStatus() != RequestStatus.PENDING) {
            return null;
        }
        return workflows.findById(r.getWorkflowId())
                .flatMap(w -> w.getSteps().stream()
                        .filter(s -> s.getStepOrder() == r.getCurrentStepOrder())
                        .findFirst())
                .map(step -> r.isEscalated() && step.getEscalationRole() != null
                        ? step.getEscalationRole() : step.getApproverRole())
                .orElse(null);
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessRuleException("Invalid payload: " + e.getMessage());
        }
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
