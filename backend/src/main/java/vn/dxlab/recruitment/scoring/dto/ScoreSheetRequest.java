package vn.dxlab.recruitment.scoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.dxlab.recruitment.scoring.Recommendation;

import java.util.List;

/**
 * Lưu phiếu chấm của chính mình. {@code version}: giá trị nhận được khi mở phiếu ({@code null} với phiếu mới).
 * {@code submit = true}: nộp phiếu; phiếu phải đủ điểm mọi tiêu chí và có đề xuất.
 * Phiếu đã nộp vẫn sửa được (đến khi kết luận được phê duyệt) nhưng luôn phải đầy đủ.
 */
public record ScoreSheetRequest(
        @NotNull List<@Valid @NotNull ScoreEntry> scores,
        @Size(max = 2000) String comment,
        Recommendation recommendation,
        Integer version,
        boolean submit) {

    public ScoreSheetRequest {
        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }
}
