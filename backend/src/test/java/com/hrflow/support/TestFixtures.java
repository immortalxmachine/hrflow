package com.hrflow.support;

import com.hrflow.domain.Country;
import com.hrflow.domain.Employee;
import com.hrflow.domain.Role;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test helper for creating employees and minting JWTs. Registered as a bean in
 * the test context (component-scanned under com.hrflow).
 */
@Component
public class TestFixtures {

    public static final String PASSWORD = "Password123!";

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AtomicInteger seq = new AtomicInteger();

    public TestFixtures(EmployeeRepository employees, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Create and persist an employee with a unique code and the shared test password. */
    public Employee employee(Role role, Country country, Employee manager) {
        Employee e = new Employee();
        int n = seq.incrementAndGet();
        e.setEmployeeCode("T" + role.name().charAt(0) + n);
        e.setFullName("Test " + role + " " + n);
        e.setEmail("t" + n + "@synthetic.hrflow.test");
        e.setCountry(country);
        e.setRole(role);
        e.setManager(manager);
        e.setPasswordHash(passwordEncoder.encode(PASSWORD));
        e.setActive(true);
        return employees.save(e);
    }

    /** An "Authorization: Bearer ..." header value for the given employee. */
    public String bearer(Employee e) {
        return "Bearer " + jwtService.issue(e);
    }
}
