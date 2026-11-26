package com.hrflow.rules;

import com.hrflow.domain.LeavePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates a leave request against an employee's country leave policy.
 *
 * <p>This class is deliberately free of database or Spring dependencies: every
 * input (dates, policy, holidays, balance, "today") is passed in, so each
 * country's rules can be unit-tested as a pure function.
 *
 * <p>Simplifying assumption (documented in the README): the weekend is
 * Saturday + Sunday for all three countries. Genuine country differences are
 * expressed through the policy numbers and the public-holiday calendar.
 */
@Component
public class LeaveRuleEngine {

    /**
     * Count business days in [start, end] inclusive, excluding weekends and the
     * supplied public holidays.
     */
    public int countWorkingDays(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            if (!weekend && !holidays.contains(d)) {
                days++;
            }
        }
        return days;
    }

    /**
     * Apply all country rules. Returns a result listing every violation found
     * (it does not stop at the first), which makes for clearer error messages
     * and easier testing.
     */
    public LeaveValidationResult validate(LocalDate start, LocalDate end,
                                          LeavePolicy policy, Set<LocalDate> holidays,
                                          BigDecimal remainingBalance, LocalDate today) {
        List<String> violations = new ArrayList<>();

        // 1. Date sanity: end must not precede start.
        if (end.isBefore(start)) {
            violations.add("End date is before start date");
            return LeaveValidationResult.of(0, violations); // other checks are meaningless
        }

        // 2. Cannot start in the past.
        if (start.isBefore(today)) {
            violations.add("Leave cannot start in the past");
        }

        // 3. Minimum notice: full days between today and start.
        long noticeDays = ChronoUnit.DAYS.between(today, start);
        if (noticeDays < policy.getMinNoticeDays()) {
            violations.add("Requires at least " + policy.getMinNoticeDays()
                    + " day(s) notice (got " + noticeDays + ")");
        }

        int workingDays = countWorkingDays(start, end, holidays);

        // 4. A request must contain at least one working day.
        if (workingDays == 0) {
            violations.add("Request contains no working days (all weekend/holiday)");
        }

        // 5. Max consecutive working days for this leave type.
        if (workingDays > policy.getMaxConsecutiveDays()) {
            violations.add("Exceeds max consecutive days of " + policy.getMaxConsecutiveDays()
                    + " (requested " + workingDays + ")");
        }

        // 6. Sufficient remaining balance.
        if (BigDecimal.valueOf(workingDays).compareTo(remainingBalance) > 0) {
            violations.add("Insufficient balance: " + remainingBalance
                    + " day(s) remaining, " + workingDays + " requested");
        }

        return LeaveValidationResult.of(workingDays, violations);
    }
}
