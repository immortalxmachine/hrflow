package com.hrflow.security;

import com.hrflow.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request. If a valid {@code Authorization: Bearer <jwt>} header
 * is present, it rebuilds the principal from the token's claims and populates
 * the SecurityContext. Invalid/expired tokens are ignored here (the request
 * then hits the authorization rules and is rejected with 401/403); they are not
 * treated as hard errors so that public endpoints still work.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                Long employeeId = claims.get("eid", Long.class);
                Role role = Role.valueOf(claims.get("role", String.class));
                AuthenticatedUser principal = new AuthenticatedUser(
                        employeeId, claims.getSubject(), "", role, true);

                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                // Malformed/expired token -> leave the context empty (unauthenticated).
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
