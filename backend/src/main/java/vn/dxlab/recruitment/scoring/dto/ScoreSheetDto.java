package vn.dxlab.recruitment.scoring.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.scoring.Recommendation;
import vn.dxlab.recruitment.track.CriterionGroup;

import java.time.Instant;
import java.util.List;

/** Phiếu chấm của người đang đăng nhập cho một ứng viên, kèm thang hướng dẫn của từng tiêu chí. */
@NullMarked
public record ScoreSheetDto(
        Long candidateId,
        String studentCode,
        String candidateName,
        String trackCode,
        List<Item> items,
        @Nullable String comment,
        @Nullable Recommendation recommendation,
        @Nullable Instant submittedAt,
        /** null với phiếu chưa lưu lần nào. */
        @Nullable Integer version,
        boolean editable,
        @Nullable String lockedReason) {

    public record Item(Long criterionId, CriterionGroup group, String name, @Nullable String description,
                       @Nullable String rubricLow, @Nullable String rubricMid, @Nullable String rubricHigh,
                       @Nullable Integer value) {
    }
}
