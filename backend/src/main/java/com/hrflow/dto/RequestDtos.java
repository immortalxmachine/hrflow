package com.hrflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.hrflow.domain.Decision;
import com.hrflow.domain.LeaveType;
import com.hrflow.domain.RequestStatus;
import com.hrflow.domain.RequestType;
import com.hrflow.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** DTOs for raising and viewing requests and approval decisions. */
public final class RequestDtos {

    private RequestDtos() { }

    /** Create a leave request for the current user (dates inclusive). */
    public record CreateLeaveRequest(
            @NotNull LeaveType leaveType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) { }

    /** Create an onboarding/expense request with a free-form JSON payload. */
    public record CreateGenericRequest(
            @NotNull RequestType requestType,
            @NotNull Map<String, Object> payload) { }

    /** Approve or reject the current step of a request. */
    public record DecisionRequest(
            @NotNull Decision decision,
            String comment) { }

    /** Full view of a request. */
    public record RequestDto(
            Long id,
            RequestType requestType,
            Long requesterId,
            RequestStatus status,
            int currentStepOrder,
            Role currentApproverRole,   // who must act now (escalation-aware)
            boolean escalated,
            JsonNode payload,
            Instant createdAt,
            Instant updatedAt) { }

    /** One recorded approval action. */
    public record ApprovalActionDto(
            int stepOrder,
            Long approverId,
            Decision decision,
            String comment,
            Instant actedAt) { }
}
