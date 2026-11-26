package com.hrflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding of the {@code hrflow.*} settings in application.yml.
 * Using records gives immutable, constructor-bound config with no boilerplate.
 */
@ConfigurationProperties(prefix = "hrflow")
public record AppProperties(
        Jwt jwt,
        Webhook webhook,
        boolean seedOnStartup
) {
    /** JWT signing settings. */
    public record Jwt(String secret, long expiryMinutes, String issuer) { }

    /** Outbound payroll webhook settings. */
    public record Webhook(String payrollUrl, String secret, int maxAttempts) { }
}
