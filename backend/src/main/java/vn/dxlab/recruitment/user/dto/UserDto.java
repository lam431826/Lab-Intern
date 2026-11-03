package vn.dxlab.recruitment.user.dto;

import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

/**
 * @param linked đã đăng nhập Clerk ít nhất một lần (đã gắn clerk_user_id); chưa thì có thể gửi lại lời mời
 */
public record UserDto(Long id, String email, String fullName, Role role, boolean active, boolean linked) {

    public static UserDto from(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isActive(), u.getClerkUserId() != null);
    }
}
