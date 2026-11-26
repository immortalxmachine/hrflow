package com.hrflow.domain;

import jakarta.persistence.*;

/**
 * Country-specific leave entitlement rule. One row per (country, leaveType).
 * Read by the country rule engine to validate a leave request.
 */
@Entity
@Table(name = "leave_policies")
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "annual_entitlement", nullable = false)
    private int annualEntitlement;

    @Column(name = "carry_forward_max", nullable = false)
    private int carryForwardMax;

    @Column(name = "min_notice_days", nullable = false)
    private int minNoticeDays;

    @Column(name = "max_consecutive_days", nullable = false)
    private int maxConsecutiveDays;

    protected LeavePolicy() { }

    public Long getId() { return id; }
    public Country getCountry() { return country; }
    public LeaveType getLeaveType() { return leaveType; }
    public int getAnnualEntitlement() { return annualEntitlement; }
    public int getCarryForwardMax() { return carryForwardMax; }
    public int getMinNoticeDays() { return minNoticeDays; }
    public int getMaxConsecutiveDays() { return maxConsecutiveDays; }
}
