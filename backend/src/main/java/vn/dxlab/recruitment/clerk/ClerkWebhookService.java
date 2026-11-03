package vn.dxlab.recruitment.clerk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import vn.dxlab.recruitment.security.UserIdentityService;
import vn.dxlab.recruitment.user.AppUserRepository;

import java.util.Optional;

/**
 * Đồng bộ thay đổi tài khoản từ Clerk. Svix có thể gửi lại một sự kiện nhiều lần,
 * nên mỗi {@code svix-id} chỉ được xử lý một lần.
 */
@Service
public class ClerkWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ClerkWebhookService.class);

    private final ProcessedWebhookRepository processed;
    private final AppUserRepository users;
    private final UserIdentityService identities;

    public ClerkWebhookService(ProcessedWebhookRepository processed, AppUserRepository users,
                               UserIdentityService identities) {
        this.processed = processed;
        this.users = users;
        this.identities = identities;
    }

    /** @return {@code false} nếu sự kiện đã được xử lý trước đó */
    @Transactional
    public boolean handle(String svixId, JsonNode event) {
        if (processed.existsById(svixId)) {
            return false;
        }
        String type = event.path("type").asString("");
        JsonNode data = event.path("data");
        String clerkUserId = data.path("id").asString(null);

        switch (type) {
            case "user.created", "user.updated" -> syncEmail(clerkUserId, primaryEmail(data));
            case "user.deleted" -> deactivate(clerkUserId);
            default -> log.debug("Bỏ qua webhook Clerk loại {}", type);
        }
        processed.save(new ProcessedWebhook(svixId, type));
        identities.evictAll();
        return true;
    }

    /** Tài khoản đã liên kết: cập nhật email nếu người dùng đổi email chính trên Clerk. */
    private void syncEmail(String clerkUserId, Optional<String> email) {
        if (clerkUserId == null || email.isEmpty()) {
            return;
        }
        users.findByClerkUserId(clerkUserId).ifPresent(u -> {
            if (!u.getEmail().equalsIgnoreCase(email.get()) && users.findByEmail(email.get()).isEmpty()) {
                log.info("Cập nhật email tài khoản {} -> {}", u.getEmail(), email.get());
                u.setEmail(email.get());
            }
        });
    }

    /** Xóa người dùng trên Clerk thì khóa tài khoản; giữ lại dữ liệu chấm điểm đã có. */
    private void deactivate(String clerkUserId) {
        if (clerkUserId == null) {
            return;
        }
        users.findByClerkUserId(clerkUserId).ifPresent(u -> {
            log.info("Khóa tài khoản {} do đã bị xóa trên Clerk", u.getEmail());
            u.setActive(false);
        });
    }

    private static Optional<String> primaryEmail(JsonNode data) {
        String primaryId = data.path("primary_email_address_id").asString(null);
        for (JsonNode address : data.path("email_addresses")) {
            if (primaryId == null || primaryId.equals(address.path("id").asString(null))) {
                return Optional.ofNullable(address.path("email_address").asString(null));
            }
        }
        return Optional.empty();
    }
}
