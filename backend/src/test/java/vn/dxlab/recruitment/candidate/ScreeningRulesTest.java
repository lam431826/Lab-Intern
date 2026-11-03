package vn.dxlab.recruitment.candidate;

import org.junit.jupiter.api.Test;
import vn.dxlab.recruitment.candidate.ScreeningRules.Flag;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScreeningRulesTest {

    @Test
    void flagsMissingOrUnusableLinks() {
        assertThat(ScreeningRules.flags(null, null, false)).containsExactly(Flag.MISSING_CV);
        assertThat(ScreeningRules.flags("drive.google.com/abc", null, false)).containsExactly(Flag.INVALID_CV_LINK);
        assertThat(ScreeningRules.flags("https://drive.google.com/abc", null, true)).containsExactly(Flag.MISSING_PORTFOLIO);
        assertThat(ScreeningRules.flags("https://cv", "behance.net/x", true)).containsExactly(Flag.INVALID_PORTFOLIO_LINK);
        assertThat(ScreeningRules.flags("HTTPS://cv", "http://behance.net/x", true)).isEmpty();
        // Ngạch không bắt buộc Portfolio thì thiếu Portfolio không bị gắn cờ
        assertThat(ScreeningRules.flags("https://cv", "  ", false)).isEmpty();
    }

    @Test
    void averagesOnlyScoredItems() {
        assertThat(ScreeningRules.cvAverage(null, null, null, null, null)).isNull();
        assertThat(ScreeningRules.cvAverage(7, 8, null, 9, null)).isEqualByComparingTo(new BigDecimal("8.0"));
        assertThat(ScreeningRules.cvAverage(7, 8, 8, 8, 8)).isEqualByComparingTo(new BigDecimal("7.8"));
        assertThat(ScreeningRules.cvAverage(7, 8)).isEqualByComparingTo(new BigDecimal("7.5"));
    }

    @Test
    void onlyPendingProfilesBecomeOverdue() {
        Instant assigned = Instant.parse("2026-08-04T02:00:00Z");
        Instant justAfter = assigned.plus(Duration.ofHours(36)).plusSeconds(1);

        assertThat(ScreeningRules.slaDeadline(assigned, 36)).isEqualTo(Instant.parse("2026-08-05T14:00:00Z"));
        assertThat(ScreeningRules.overdue(ScreeningStatus.PENDING, assigned, 36, justAfter)).isTrue();
        assertThat(ScreeningRules.overdue(ScreeningStatus.PENDING, assigned, 36, justAfter.minusSeconds(2))).isFalse();
        assertThat(ScreeningRules.overdue(ScreeningStatus.PASSED, assigned, 36, justAfter)).isFalse();
    }
}
