package vn.dxlab.recruitment.candidate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Người tổ chức thêm hoặc sửa thông tin hồ sơ (không gồm kết quả sàng lọc). */
public record CandidateRequest(
        @NotBlank(message = "Mã SV không được để trống") @Size(max = 20, message = "Mã SV tối đa 20 ký tự") String studentCode,
        @NotBlank(message = "Họ tên không được để trống") @Size(max = 100) String fullName,
        @Email(message = "Email không đúng định dạng") @Size(max = 254) String email,
        @Size(max = 20) String phone,
        @Size(max = 100) String major,
        @NotNull(message = "Chưa chọn ngạch") Long trackId,
        @Size(max = 500) String cvLink,
        @Size(max = 500) String portfolioLink) {

    public CandidateRequest {
        studentCode = trim(studentCode) == null ? null : trim(studentCode).toUpperCase(java.util.Locale.ROOT);
        fullName = trim(fullName);
        email = trim(email) == null ? null : trim(email).toLowerCase(java.util.Locale.ROOT);
        phone = trim(phone);
        major = trim(major);
        cvLink = trim(cvLink);
        portfolioLink = trim(portfolioLink);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
