package com.hrflow.rules;

import com.hrflow.domain.Country;
import com.hrflow.domain.LeavePolicy;
import com.hrflow.domain.LeaveType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the country leave rule engine (no Spring, no DB).
 * Each country's policy produces different pass/fail outcomes for the same
 * request shape, which is the point of the country-specific rules.
 */
class LeaveRuleEngineTest {

    private final LeaveRuleEngine engine = new LeaveRuleEngine();

    // Policies mirroring the seeded reference data.
    private final LeavePolicy indiaAnnual = new LeavePolicy(Country.IN, LeaveType.ANNUAL, 18, 30, 3, 15);
    private final LeavePolicy uaeAnnual   = new LeavePolicy(Country.AE, LeaveType.ANNUAL, 30, 15, 7, 30);
    private final LeavePolicy germanyAnnual = new LeavePolicy(Country.DE, LeaveType.ANNUAL, 20, 10, 14, 20);

    private final LocalDate today = LocalDate.of(2026, 1, 1); // a Thursday

    @Test
    void countsOnlyWorkingDays_excludingWeekendsAndHolidays() {
        // Mon 2026-06-01 .. Fri 2026-06-05 = 5 working days; make Wed a holiday -> 4.
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 6, 3));
        int days = engine.countWorkingDays(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), holidays);
        assertThat(days).isEqualTo(4);
    }

    @Test
    void india_validRequestPasses() {
        // 5 working days, 5 months ahead -> satisfies 3-day notice, within 15 max, balance ok.
        var result = engine.validate(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5),
                indiaAnnual, Set.of(), BigDecimal.valueOf(18), today);
        assertThat(result.valid()).isTrue();
        assertThat(result.workingDays()).isEqualTo(5);
    }

    @Test
    void germany_shortNoticeFails() {
        // Germany requires 14 days notice; start only 2 days out.
        var result = engine.validate(today.plusDays(2), today.plusDays(4),
                germanyAnnual, Set.of(), BigDecimal.valueOf(20), today);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("notice"));
    }

    @Test
    void india_sameRangeThatFailsGermanyNoticePasses() {
        // India only needs 3 days notice, so a 5-day-out request is fine there.
        var result = engine.validate(today.plusDays(5), today.plusDays(7),
                indiaAnnual, Set.of(), BigDecimal.valueOf(18), today);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void uae_exceedingMaxConsecutiveFails() {
        // 31 working days (via a long span with no holidays) > UAE max of 30.
        LocalDate start = nextMonday(LocalDate.of(2026, 3, 2));
        LocalDate end = start.plusWeeks(7); // ~ 7.x weeks -> >30 working days
        var result = engine.validate(start, end, uaeAnnual, Set.of(), BigDecimal.valueOf(100), today);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("max consecutive"));
    }

    @Test
    void insufficientBalanceFails() {
        // 5 working days requested but only 2 remaining.
        var result = engine.validate(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5),
                indiaAnnual, Set.of(), BigDecimal.valueOf(2), today);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("balance"));
    }

    @Test
    void endBeforeStartFails() {
        var result = engine.validate(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 1),
                indiaAnnual, Set.of(), BigDecimal.valueOf(18), today);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void startInThePastFails() {
        var result = engine.validate(today.minusDays(1), today.plusDays(1),
                indiaAnnual, Set.of(), BigDecimal.valueOf(18), today);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("past"));
    }

    private LocalDate nextMonday(LocalDate d) {
        while (d.getDayOfWeek().getValue() != 1) {
            d = d.plusDays(1);
        }
        return d;
    }
}
