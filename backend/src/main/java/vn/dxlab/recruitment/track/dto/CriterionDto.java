package vn.dxlab.recruitment.track.dto;

import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.track.CriterionGroup;

/**
 * @param rubricLow  hướng dẫn chấm mức Trung bình (1–6)
 * @param rubricMid  hướng dẫn chấm mức Khá – Đạt (7–8)
 * @param rubricHigh hướng dẫn chấm mức Xuất sắc (9–10)
 */
public record CriterionDto(Long id, Long trackId, CriterionGroup group, String name, String description,
                           String rubricLow, String rubricMid, String rubricHigh, int sortOrder, boolean active) {

    public static CriterionDto from(Criterion c) {
        return new CriterionDto(c.getId(), c.getTrack() == null ? null : c.getTrack().getId(), c.getGroup(),
                c.getName(), c.getDescription(), c.getRubricLow(), c.getRubricMid(), c.getRubricHigh(),
                c.getSortOrder(), c.isActive());
    }
}
