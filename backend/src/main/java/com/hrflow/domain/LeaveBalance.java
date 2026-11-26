package com.hrflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * An employee's leave balance for a given type and calendar year.
 * remaining = entitled + carried - used.
 */
@Entity
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private int year;

    @Column(name = "entitled_days", nullable = false)
    private BigDecimal entitledDays;

    @Column(name = "used_days", nullable = false)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "carried_days", nullable = false)
    private BigDecimal carriedDays = BigDecimal.ZERO;

    protected LeaveBalance() { }

    public LeaveBalance(Long employeeId, LeaveType leaveType, int year,
                        BigDecimal entitledDays, BigDecimal carriedDays) {
        this.employeeId = employeeId;
        this.leaveType = leaveType;
        this.year = year;
        this.entitledDays = entitledDays;
        this.carriedDays = carriedDays;
    }

    /** Days still available to book. */
    public BigDecimal remaining() {
        return entitledDays.add(carriedDays).subtract(usedDays);
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public LeaveType getLeaveType() { return leaveType; }
    public int getYear() { return year; }
    public BigDecimal getEntitledDays() { return entitledDays; }
    public BigDecimal getUsedDays() { return usedDays; }
    public void setUsedDays(BigDecimal usedDays) { this.usedDays = usedDays; }
    public BigDecimal getCarriedDays() { return carriedDays; }
}
