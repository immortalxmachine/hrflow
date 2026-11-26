package com.hrflow.service;

import com.hrflow.config.AppProperties;
import com.hrflow.domain.Employee;
import com.hrflow.dto.AuthDtos.LoginRequest;
import com.hrflow.dto.AuthDtos.LoginResponse;
import com.hrflow.repo.EmployeeRepository;
import com.hrflow.security.JwtService;
import com.hrflow.web.error.ApiExceptions.NotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Authenticates an employee by code + password and issues a JWT.
 * Password verification is delegated to the AuthenticationManager (which uses
 * our UserDetailsService + BCrypt). A bad password throws an
 * AuthenticationException, mapped to 401 by the global handler.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmployeeRepository employees;
    private final long expiryMinutes;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       EmployeeRepository employees, AppProperties props) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.employees = employees;
        this.expiryMinutes = props.jwt().expiryMinutes();
    }

    public LoginResponse login(LoginRequest req) {
        // Throws AuthenticationException on bad credentials / disabled account.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.employeeCode(), req.password()));

        Employee e = employees.findByEmployeeCode(req.employeeCode())
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        String token = jwtService.issue(e);
        return new LoginResponse(token, expiryMinutes, e.getId(), e.getEmployeeCode(),
                e.getFullName(), e.getRole(), e.getCountry());
    }
}
