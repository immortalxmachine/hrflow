package com.hrflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * An employee. Doubles as the authentication principal: the login identity,
 * password hash and role all live here.
 *
 * <p>{@code manager} and {@code hrbp} are self-references used by the workflow
 * engine to resolve who the MANAGER / HR_ADMIN approver is for a given request.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String department;

    // --- Real attributes from the IBM HR Attrition dataset (seed source). ---
    // Reference/display only: the workflow, RBAC and leave engine never read
    // these. Nullable so the CSV importer and test fixtures can omit them.
    @Column(name = "age")
    private Integer age;

    @Column(name = "gender")
    private String gender;

    @Column(name = "job_role")
    private String jobRole;

    @Column(name = "job_level")
    private Integer jobLevel;

    @Column(name = "monthly_income")
    private Integer monthlyIncome;

    @Column(name = "marital_status")
    private String maritalStatus;

    // Direct manager; the level-1 approver for most flows. Lazy to avoid
    // loading the whole management chain on every fetch.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    // HR business partner; a convenient explicit level-2 approver.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hrbp_id")
    private Employee hrbp;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Employee() { } // required by JPA; also used by the importer/seeder

    // --- getters / setters ---
    public Long getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Country getCountry() { return country; }
    public void setCountry(Country country) { this.country = country; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }
    public Integer getJobLevel() { return jobLevel; }
    public void setJobLevel(Integer jobLevel) { this.jobLevel = jobLevel; }
    public Integer getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(Integer monthlyIncome) { this.monthlyIncome = monthlyIncome; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public Employee getManager() { return manager; }
    public void setManager(Employee manager) { this.manager = manager; }
    public Employee getHrbp() { return hrbp; }
    public void setHrbp(Employee hrbp) { this.hrbp = hrbp; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
