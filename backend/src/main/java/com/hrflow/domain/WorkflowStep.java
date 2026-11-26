package com.hrflow.domain;

import jakarta.persistence.*;

/**
 * One approval level within a {@link WorkflowDefinition}.
 *
 * <p>{@code slaHours} is how long the step may sit before it is "overdue";
 * {@code escalationRole} is who it escalates to then (nullable = no escalation).
 */
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id")
    private WorkflowDefinition workflow;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false)
    private Role approverRole;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours = 48;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_role")
    private Role escalationRole;

    protected WorkflowStep() { }

    public WorkflowStep(int stepOrder, Role approverRole, int slaHours, Role escalationRole) {
        this.stepOrder = stepOrder;
        this.approverRole = approverRole;
        this.slaHours = slaHours;
        this.escalationRole = escalationRole;
    }

    public Long getId() { return id; }
    public WorkflowDefinition getWorkflow() { return workflow; }
    public void setWorkflow(WorkflowDefinition workflow) { this.workflow = workflow; }
    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
    public Role getApproverRole() { return approverRole; }
    public void setApproverRole(Role approverRole) { this.approverRole = approverRole; }
    public int getSlaHours() { return slaHours; }
    public void setSlaHours(int slaHours) { this.slaHours = slaHours; }
    public Role getEscalationRole() { return escalationRole; }
    public void setEscalationRole(Role escalationRole) { this.escalationRole = escalationRole; }
}
