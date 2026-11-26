package com.hrflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience access to the current authenticated principal. */
public final class SecurityUtils {

    private SecurityUtils() { }

    /** The current principal, or {@code null} if the request is unauthenticated. */
    public static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
