package com.hrflow.dto;

import com.hrflow.domain.RequestType;
import com.hrflow.domain.Role;
import com.hrflow.domain.WorkflowDefinition;
import com.hrflow.domain.WorkflowStep;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** DTOs for the workflow-definition admin API. */
public final class WorkflowDtos {

    private WorkflowDtos() { }

    /** One approval step in a chain. */
    public record StepDto(
            @Positive int stepOrder,
            @NotNull Role approverRole,
            @Positive int slaHours,
            Role escalationRole) {

        static StepDto from(WorkflowStep s) {
            return new StepDto(s.getStepOrder(), s.getApproverRole(), s.getSlaHours(), s.getEscalationRole());
        }
    }

    /** Full view of a workflow definition. */
    public record WorkflowDto(
            Long id,
            RequestType requestType,
            String name,
            boolean active,
            List<StepDto> steps) {

        public static WorkflowDto from(WorkflowDefinition d) {
            return new WorkflowDto(d.getId(), d.getRequestType(), d.getName(), d.isActive(),
                    d.getSteps().stream().map(StepDto::from).toList());
        }
    }

    /** Create payload. */
    public record CreateWorkflowRequest(
            @NotNull RequestType requestType,
            @NotEmpty String name,
            @NotEmpty @Valid List<StepDto> steps) { }

    /** Update payload (name, active flag and full step replacement). */
    public record UpdateWorkflowRequest(
            @NotEmpty String name,
            boolean active,
            @NotEmpty @Valid List<StepDto> steps) { }
}
