package vn.dxlab.recruitment.track.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.track.CriterionGroup;

/**
 * @param rubricLow  hướng dẫn chấm mức Trung bình (1–6)
 * @param rubricMid  hướng dẫn chấm mức Khá – Đạt (7–8)
 * @param rubricHigh hướng dẫn chấm mức Xuất sắc (9–10)
 */
@NullMarked
public record CriterionDto(Long id, @Nullable Long trackId, CriterionGroup group, String name,
                           @Nullable String description, @Nullable String rubricLow,
                           @Nullable String rubricMid, @Nullable String rubricHigh, int sortOrder, boolean active) {

    public static CriterionDto from(Criterion c) {
        return new CriterionDto(c.getId(), c.getTrack() == null ? null : c.getTrack().getId(), c.getGroup(),
                c.getName(), c.getDescription(), c.getRubricLow(), c.getRubricMid(), c.getRubricHigh(),
                c.getSortOrder(), c.isActive());
    }
}
