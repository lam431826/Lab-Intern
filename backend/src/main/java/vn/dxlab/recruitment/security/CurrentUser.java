package vn.dxlab.recruitment.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.dxlab.recruitment.user.Role;

/** Truy cập người dùng hiện tại trong service mà không phải truyền tham số qua mọi lớp. */
@Component
public class CurrentUser {

    public AppUserPrincipal get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p;
        }
        throw new AccessDeniedException("Chưa đăng nhập hoặc tài khoản chưa được cấp quyền");
    }

    public Long id() {
        return get().id();
    }

    public boolean hasRole(Role role) {
        return get().role() == role;
    }
}
