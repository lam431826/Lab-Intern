package vn.dxlab.recruitment.result.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.dxlab.recruitment.result.FinalStatus;

/** {@code version}: {@code null} khi ứng viên chưa có kết luận. */
public record DecisionRequest(
        @NotNull FinalStatus finalStatus,
        Long transferTrackId,
        @Size(max = 1000) String note,
        Integer version) {

    public DecisionRequest {
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
