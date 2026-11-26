package com.hrflow.web.error;

/**
 * Application exceptions, grouped in one file for brevity. Each maps to a
 * specific HTTP status in {@link GlobalExceptionHandler}.
 */
public final class ApiExceptions {

    private ApiExceptions() { }

    /** Requested entity does not exist -> 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    /** A business/validation rule was violated (e.g. not enough leave balance) -> 422. */
    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) { super(message); }
    }

    /** A uniqueness or state conflict (e.g. duplicate active workflow) -> 409. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
