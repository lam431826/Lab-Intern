package vn.dxlab.recruitment.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;

import java.util.Optional;

/**
 * Ánh xạ danh tính Clerk ({@code sub}) sang tài khoản của hệ thống.
 *
 * <p>Lần đăng nhập đầu tiên, tài khoản chưa có {@code clerk_user_id}: tìm tài khoản đã được
 * Người tổ chức tạo sẵn theo email (claim {@code email} cấu hình trong session token Clerk)
 * rồi gắn {@code sub} vào. Không có tài khoản tạo sẵn thì không được vào, kể cả khi đăng nhập
 * Clerk thành công.
 */
@Service
public class UserIdentityService {

    public static final String CACHE = "principalsByClerkId";
    private static final Logger log = LoggerFactory.getLogger(UserIdentityService.class);

    private final AppUserRepository users;

    public UserIdentityService(AppUserRepository users) {
        this.users = users;
    }

    /** @return người dùng đang hoạt động, hoặc {@code null} nếu chưa được cấp quyền / đã bị khóa */
    @Transactional
    @Cacheable(cacheNames = CACHE, key = "#clerkUserId", unless = "#result == null")
    public AppUserPrincipal resolve(String clerkUserId, String email) {
        Optional<AppUser> linked = users.findByClerkUserId(clerkUserId);
        if (linked.isPresent()) {
            return linked.get().isActive() ? toPrincipal(linked.get()) : null;
        }
        if (email == null || email.isBlank()) {
            return null;
        }
        return users.findByEmail(email.trim())
                .filter(u -> u.isActive() && u.getClerkUserId() == null)
                .map(u -> {
                    u.setClerkUserId(clerkUserId);
                    log.info("Liên kết tài khoản {} với Clerk user {}", u.getEmail(), clerkUserId);
                    return toPrincipal(users.saveAndFlush(u));
                })
                .orElse(null);
    }

    /** Gọi sau khi đổi vai trò, khóa tài khoản hoặc nhận webhook, để request kế tiếp đọc lại DB. */
    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public void evictAll() {
        // Spring Cache xóa cache khi phương thức được gọi
    }

    private static AppUserPrincipal toPrincipal(AppUser u) {
        return new AppUserPrincipal(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
    }
}
