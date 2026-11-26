package com.hrflow.web;

import com.hrflow.dto.WorkflowDtos.CreateWorkflowRequest;
import com.hrflow.dto.WorkflowDtos.UpdateWorkflowRequest;
import com.hrflow.dto.WorkflowDtos.WorkflowDto;
import com.hrflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for managing approval workflows. Authorization is enforced in
 * {@link WorkflowService} (method security), so a non-admin caller gets 403.
 */
@RestController
@RequestMapping("/api/admin/workflows")
public class WorkflowController {

    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping
    public List<WorkflowDto> list() {
        return workflows.list();
    }

    @GetMapping("/{id}")
    public WorkflowDto get(@PathVariable Long id) {
        return workflows.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDto create(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflows.create(request);
    }

    @PutMapping("/{id}")
    public WorkflowDto update(@PathVariable Long id, @Valid @RequestBody UpdateWorkflowRequest request) {
        return workflows.update(id, request);
    }
}
