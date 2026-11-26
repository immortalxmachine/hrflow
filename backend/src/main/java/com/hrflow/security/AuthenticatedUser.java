package com.hrflow.security;

import com.hrflow.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal placed in the SecurityContext. Implements Spring
 * Security's {@link UserDetails} so it works with both the login flow (where it
 * carries the password hash) and the JWT filter (where it is rebuilt from
 * token claims with no password).
 *
 * <p>The single authority is {@code ROLE_<role>}, which drives
 * {@code hasRole(...)} checks in {@code @PreAuthorize}.
 */
public class AuthenticatedUser implements UserDetails {

    private final Long employeeId;
    private final String employeeCode;
    private final String passwordHash;   // empty when rebuilt from a JWT
    private final Role role;
    private final boolean active;

    public AuthenticatedUser(Long employeeId, String employeeCode, String passwordHash,
                             Role role, boolean active) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public Long getEmployeeId() { return employeeId; }
    public Role getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return employeeCode; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
