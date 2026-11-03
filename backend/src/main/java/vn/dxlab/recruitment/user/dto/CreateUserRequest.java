package vn.dxlab.recruitment.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.dxlab.recruitment.user.Role;

/**
 * @param sendInvite gửi email mời qua Clerk ngay; mặc định có
 */
public record CreateUserRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 254) String email,
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
        @NotNull(message = "Chưa chọn vai trò") Role role,
        Boolean sendInvite) {

    /** Cắt khoảng trắng thừa (hay gặp khi dán từ Excel) trước khi kiểm tra định dạng. */
    public CreateUserRequest {
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        fullName = fullName == null ? null : fullName.trim();
    }

    public boolean shouldSendInvite() {
        return sendInvite == null || sendInvite;
    }
}
