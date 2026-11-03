package vn.dxlab.recruitment.scoring;

import org.junit.jupiter.api.Test;
import vn.dxlab.recruitment.scoring.ScoreCalculator.CriterionRef;
import vn.dxlab.recruitment.track.CriterionGroup;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTest {

    private static final List<CriterionRef> SHEET = List.of(
            new CriterionRef(1L, CriterionGroup.GENERAL), new CriterionRef(2L, CriterionGroup.GENERAL),
            new CriterionRef(3L, CriterionGroup.PROFESSIONAL), new CriterionRef(4L, CriterionGroup.PROFESSIONAL));
    private static final BigDecimal W_GENERAL = new BigDecimal("0.40");
    private static final BigDecimal W_PROFESSIONAL = new BigDecimal("0.60");

    /**
     * Phép tính tay: tiêu chí 1 = (8+7+9)/3 = 8; tiêu chí 2 = (7+7+6)/3 = 6,6667; chung = 7,3333.
     * Tiêu chí 3 = (9+8+7)/3 = 8; tiêu chí 4 = (6+8)/2 = 7 (giám khảo C bỏ trống); chuyên môn = 7,5.
     * Tổng = 0,4 × 7,3333 + 0,6 × 7,5 = 7,4333 → 7,43.
     */
    @Test
    void matchesHandCalculation() {
        var r = ScoreCalculator.calculate(SHEET, List.of(
                Map.of(1L, 8, 2L, 7, 3L, 9, 4L, 6),
                Map.of(1L, 7, 2L, 7, 3L, 8, 4L, 8),
                Map.of(1L, 9, 2L, 6, 3L, 7)), W_GENERAL, W_PROFESSIONAL);

        assertThat(r.criterionAverages()).containsEntry(1L, new BigDecimal("8.00")).containsEntry(2L, new BigDecimal("6.67"))
                .containsEntry(4L, new BigDecimal("7.00"));
        assertThat(r.criterionCounts()).containsEntry(4L, 2);
        assertThat(r.generalAverage()).isEqualByComparingTo("7.33");
        assertThat(r.professionalAverage()).isEqualByComparingTo("7.50");
        assertThat(r.total()).isEqualByComparingTo("7.43");
        assertThat(r.meets(new BigDecimal("7.00"))).isTrue();
        assertThat(r.meets(new BigDecimal("7.50"))).isFalse();
    }

    @Test
    void totalIsMissingUntilBothGroupsAreScored() {
        var r = ScoreCalculator.calculate(SHEET, List.of(Map.of(1L, 8, 2L, 8)), W_GENERAL, W_PROFESSIONAL);
        assertThat(r.generalAverage()).isEqualByComparingTo("8.00");
        assertThat(r.professionalAverage()).isNull();
        assertThat(r.total()).isNull();
        assertThat(r.meets(BigDecimal.ONE)).isFalse();
        assertThat(ScoreCalculator.calculate(SHEET, List.of(), W_GENERAL, W_PROFESSIONAL).total()).isNull();
    }
}
