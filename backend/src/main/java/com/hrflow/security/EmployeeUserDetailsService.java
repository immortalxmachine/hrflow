package com.hrflow.security;

import com.hrflow.domain.Employee;
import com.hrflow.repo.EmployeeRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads an employee by code for the login flow. The returned principal carries
 * the bcrypt hash so the AuthenticationManager can verify the password.
 */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employees;

    public EmployeeUserDetailsService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public UserDetails loadUserByUsername(String employeeCode) throws UsernameNotFoundException {
        Employee e = employees.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown employee: " + employeeCode));
        return new AuthenticatedUser(e.getId(), e.getEmployeeCode(), e.getPasswordHash(),
                e.getRole(), e.isActive());
    }
}
