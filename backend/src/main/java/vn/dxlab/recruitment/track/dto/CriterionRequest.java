package vn.dxlab.recruitment.track.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param trackId NULL = tiêu chí chung cho mọi ngạch (chỉ Người tổ chức tạo được)
 */
public record CriterionRequest(
        Long trackId,
        @NotBlank(message = "Tên tiêu chí không được để trống") @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 1000) String rubricLow,
        @Size(max = 1000) String rubricMid,
        @Size(max = 1000) String rubricHigh,
        @Min(0) int sortOrder) {
}
