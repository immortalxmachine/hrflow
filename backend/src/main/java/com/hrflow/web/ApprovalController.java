package com.hrflow.web;

import com.hrflow.dto.RequestDtos.ApprovalActionDto;
import com.hrflow.dto.RequestDtos.DecisionRequest;
import com.hrflow.dto.RequestDtos.RequestDto;
import com.hrflow.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Approvals inbox and decision endpoints. Eligibility is enforced in the service. */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    /** Requests the current user is eligible to approve now. */
    @GetMapping("/inbox")
    public List<RequestDto> inbox() {
        return approvals.inbox();
    }

    /** Record an approve/reject decision on a request's current step. */
    @PostMapping("/{requestId}/decision")
    public RequestDto decide(@PathVariable Long requestId, @Valid @RequestBody DecisionRequest decision) {
        return approvals.decide(requestId, decision);
    }

    /** The decision history of a request. */
    @GetMapping("/{requestId}/history")
    public List<ApprovalActionDto> history(@PathVariable Long requestId) {
        return approvals.history(requestId);
    }
}
