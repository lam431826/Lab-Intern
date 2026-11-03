package vn.dxlab.recruitment.security;

import vn.dxlab.recruitment.user.Role;

/** Người dùng đã xác thực, được dựng từ token Clerk và bảng app_users. */
public record AppUserPrincipal(Long id, String email, String fullName, Role role) {
}
