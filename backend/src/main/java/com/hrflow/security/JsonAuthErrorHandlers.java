package com.hrflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Returns JSON (not an HTML error page) for the two security outcomes:
 *  - 401 when no/invalid credentials are supplied (entry point)
 *  - 403 when the user is authenticated but lacks the required role
 */
@Component
public class JsonAuthErrorHandlers {

    private final ObjectMapper mapper;

    public JsonAuthErrorHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @org.springframework.context.annotation.Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }

    @org.springframework.context.annotation.Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), Map.of(
                "status", status,
                "error", message,
                "timestamp", Instant.now().toString()));
    }
}
