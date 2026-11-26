package com.hrflow.domain;

/**
 * Access-control roles, ordered loosely from least to most privileged.
 * Stored in the DB as the enum name (see {@code employees.role}).
 */
public enum Role {
    EMPLOYEE,
    MANAGER,
    HR_ADMIN,
    SYSTEM_ADMIN
}
