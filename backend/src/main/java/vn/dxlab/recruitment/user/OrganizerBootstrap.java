package vn.dxlab.recruitment.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.clerk.ClerkInvitationClient;

import java.util.Locale;

/**
 * Tạo tài khoản Người tổ chức đầu tiên khi triển khai mới, vì chỉ Người tổ chức mới tạo được tài khoản khác.
 * Chỉ chạy khi đặt {@code BOOTSTRAP_ORGANIZER_EMAIL} và hệ thống chưa có Người tổ chức nào đang hoạt động;
 * các lần khởi động sau không làm gì. Người này đăng nhập Clerk bằng đúng email đó để liên kết tài khoản.
 *
 * <p>Clerk ở chế độ đăng ký Restricted chỉ cho người được mời đăng ký, nên tạo xong là gửi lời mời Clerk
 * (nếu đã cấu hình CLERK_SECRET_KEY). Gửi lỗi chỉ ghi log: Người tổ chức vẫn có thể được mời tay trên Clerk Dashboard.
 */
@Component
public class OrganizerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizerBootstrap.class);

    private final AppUserRepository users;
    private final ClerkInvitationClient invitations;
    private final String frontendUrl;
    private final String email;

    public OrganizerBootstrap(AppUserRepository users, ClerkInvitationClient invitations,
                              @Value("${app.frontend-url}") String frontendUrl,
                              @Value("${app.bootstrap.organizer-email:}") String email) {
        this.users = users;
        this.invitations = invitations;
        this.frontendUrl = frontendUrl;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || users.countByRoleAndActiveTrue(Role.ORGANIZER) > 0) {
            return;
        }
        AppUser existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            log.warn("BOOTSTRAP_ORGANIZER_EMAIL {} đã có tài khoản vai trò {} (active={}); chuyển thành Người tổ chức",
                    email, existing.getRole(), existing.isActive());
            existing.setRole(Role.ORGANIZER);
            existing.setActive(true);
            if (existing.getClerkUserId() == null) {
                invite();
            }
            return;
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName("Người tổ chức");
        u.setRole(Role.ORGANIZER);
        users.save(u);
        log.info("Đã tạo tài khoản Người tổ chức đầu tiên cho {}. Có thể xóa BOOTSTRAP_ORGANIZER_EMAIL khỏi .env.", email);
        invite();
    }

    private void invite() {
        try {
            invitations.invite(email, frontendUrl);
            log.info("Đã gửi lời mời Clerk tới {}", email);
        } catch (RuntimeException e) {
            log.warn("Không gửi được lời mời Clerk tới {} ({}). Mời tay trên Clerk Dashboard: Users → Invitations.",
                    email, e.getMessage());
        }
    }
}
