package vn.dxlab.recruitment.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;

/** Tài khoản nhân sự lab. Danh tính do Clerk quản lý, vai trò do hệ thống quản lý. */
@Getter
@Setter
@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    /** {@code sub} trong token Clerk; NULL cho tới lần đăng nhập đầu tiên. */
    @Column(name = "clerk_user_id", length = 64)
    private String clerkUserId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;
}
