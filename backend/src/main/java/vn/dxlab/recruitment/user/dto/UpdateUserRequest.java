package vn.dxlab.recruitment.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.dxlab.recruitment.user.Role;

/** Email không sửa ở đây: email gắn với tài khoản Clerk và được đồng bộ qua webhook. */
public record UpdateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
        @NotNull(message = "Chưa chọn vai trò") Role role,
        boolean active) {
}
