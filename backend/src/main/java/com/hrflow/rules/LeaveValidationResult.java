package com.hrflow.rules;

import java.util.List;

/**
 * Outcome of validating a leave request against a country policy.
 *
 * @param valid        true when there are no violations
 * @param workingDays  business days requested (excludes weekends + holidays)
 * @param violations   human-readable reasons the request is invalid (empty if valid)
 */
public record LeaveValidationResult(boolean valid, int workingDays, List<String> violations) {

    public static LeaveValidationResult of(int workingDays, List<String> violations) {
        return new LeaveValidationResult(violations.isEmpty(), workingDays, List.copyOf(violations));
    }
}
