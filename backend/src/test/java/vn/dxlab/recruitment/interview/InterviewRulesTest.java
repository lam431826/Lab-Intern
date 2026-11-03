package vn.dxlab.recruitment.interview;

import org.junit.jupiter.api.Test;
import vn.dxlab.recruitment.interview.InterviewRules.TimeRange;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewRulesTest {

    private static LocalTime t(int h, int m) {
        return LocalTime.of(h, m);
    }

    @Test
    void checksInterviewWindows() {
        assertThat(InterviewRules.withinWindows(t(10, 0), t(12, 30))).isTrue();
        assertThat(InterviewRules.withinWindows(t(16, 15), t(17, 0))).isTrue();
        assertThat(InterviewRules.withinWindows(t(9, 45), t(11, 0))).isFalse();
        // Vắt qua giờ nghỉ trưa thì không nằm trọn trong khung nào
        assertThat(InterviewRules.withinWindows(t(12, 0), t(16, 30))).isFalse();
    }

    @Test
    void findsFreeSlotsSkippingTakenTimes() {
        TimeRange session = new TimeRange(t(10, 0), t(11, 0));
        List<TimeRange> taken = List.of(new TimeRange(t(10, 15), t(10, 30)));

        assertThat(InterviewRules.freeSlots(session, taken, 15, 5)).containsExactly(
                new TimeRange(t(10, 0), t(10, 15)),
                new TimeRange(t(10, 30), t(10, 45)),
                new TimeRange(t(10, 45), t(11, 0)));
        assertThat(InterviewRules.freeSlots(session, List.of(), 20, 2)).hasSize(2);
        assertThat(InterviewRules.freeSlots(new TimeRange(t(23, 50), t(23, 59)), List.of(), 15, 1)).isEmpty();
    }

    @Test
    void labelsLikeLegacySheet() {
        assertThat(InterviewRules.label(t(10, 0), t(10, 15))).isEqualTo("10h - 10h15");
        assertThat(InterviewRules.label(t(17, 45), t(18, 0))).isEqualTo("17h45 - 18h");
    }
}
