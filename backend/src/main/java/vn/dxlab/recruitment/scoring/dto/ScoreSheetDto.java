package vn.dxlab.recruitment.scoring.dto;

import vn.dxlab.recruitment.scoring.Recommendation;
import vn.dxlab.recruitment.track.CriterionGroup;

import java.time.Instant;
import java.util.List;

/** Phiếu chấm của người đang đăng nhập cho một ứng viên, kèm thang hướng dẫn của từng tiêu chí. */
public record ScoreSheetDto(
        Long candidateId,
        String studentCode,
        String candidateName,
        String trackCode,
        List<Item> items,
        String comment,
        Recommendation recommendation,
        Instant submittedAt,
        Integer version,
        boolean editable,
        String lockedReason) {

    public record Item(Long criterionId, CriterionGroup group, String name, String description,
                       String rubricLow, String rubricMid, String rubricHigh, Integer value) {
    }
}
