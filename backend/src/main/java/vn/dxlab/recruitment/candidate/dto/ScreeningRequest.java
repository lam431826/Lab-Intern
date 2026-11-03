package vn.dxlab.recruitment.candidate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.dxlab.recruitment.candidate.ScreeningStatus;

/**
 * Phiếu sàng lọc CV của Lead. 5 mục chấm theo tab "Đánh giá CV": thông tin cá nhân, kinh nghiệm,
 * kỹ năng, dự án cá nhân, chứng chỉ (1–10, được bỏ trống).
 *
 * @param version giá trị {@code version} lúc mở hồ sơ; khác với hiện tại nghĩa là đã có người sửa trước
 */
public record ScreeningRequest(
        @Min(1) @Max(10) Integer cvPersonalInfo,
        @Min(1) @Max(10) Integer cvExperience,
        @Min(1) @Max(10) Integer cvSkills,
        @Min(1) @Max(10) Integer cvProjects,
        @Min(1) @Max(10) Integer cvCertificates,
        @NotNull(message = "Chưa chọn trạng thái") ScreeningStatus status,
        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason,
        @NotNull Integer version) {

    public ScreeningRequest {
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
