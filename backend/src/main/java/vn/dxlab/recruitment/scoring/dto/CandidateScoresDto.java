package vn.dxlab.recruitment.scoring.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.scoring.Recommendation;
import vn.dxlab.recruitment.track.CriterionGroup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Điểm của cả hội đồng cho một ứng viên. Chỉ tính phiếu đã nộp; phiếu nháp của người khác không hiển thị.
 * {@code total}: tổng có trọng số theo cài đặt hiện tại; {@code null} khi chưa đủ cả hai khối.
 */
@NullMarked
public record CandidateScoresDto(
        Long candidateId,
        int panelSize,
        int submittedCount,
        List<CriterionAverage> criteria,
        @Nullable BigDecimal generalAverage,
        @Nullable BigDecimal professionalAverage,
        @Nullable BigDecimal total,
        List<EvaluatorSheet> evaluators) {

    public record CriterionAverage(Long criterionId, CriterionGroup group, String name, @Nullable BigDecimal average,
                                   int count) {
    }

    /** {@code values}: criterionId → điểm. */
    public record EvaluatorSheet(Long evaluatorId, String name, Instant submittedAt, Recommendation recommendation,
                                 @Nullable String comment, Map<Long, Integer> values) {
    }
}
