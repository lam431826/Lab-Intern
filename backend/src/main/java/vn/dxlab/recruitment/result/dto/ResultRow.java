package vn.dxlab.recruitment.result.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.result.FinalStatus;

import java.math.BigDecimal;

/**
 * Một dòng bảng kết quả.
 *
 * @param interview   NOT_SCHEDULED, SCHEDULED, ATTENDED, ABSENT
 * @param rank        thứ hạng trong ngạch, chỉ xếp ứng viên đã phỏng vấn và có điểm tổng
 * @param suggestion  gợi ý theo điểm sàn và chỉ tiêu; kết luận thật nằm trong {@code decision}
 */
@NullMarked
public record ResultRow(
        Long candidateId,
        String studentCode,
        String fullName,
        String interview,
        int submittedSheets,
        int panelSize,
        long passVotes,
        long failVotes,
        @Nullable BigDecimal generalAverage,
        @Nullable BigDecimal professionalAverage,
        @Nullable BigDecimal total,
        boolean meetsThreshold,
        @Nullable Integer rank,
        @Nullable FinalStatus suggestion,
        @Nullable DecisionDto decision) {

    public boolean scoresComplete() {
        return "ATTENDED".equals(interview) && panelSize > 0 && submittedSheets >= panelSize;
    }
}
