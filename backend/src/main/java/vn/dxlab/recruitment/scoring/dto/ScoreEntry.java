package vn.dxlab.recruitment.scoring.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code value = null}: xóa ô điểm (phiếu nháp). */
public record ScoreEntry(@NotNull Long criterionId, @Min(1) @Max(10) Integer value) {
}
