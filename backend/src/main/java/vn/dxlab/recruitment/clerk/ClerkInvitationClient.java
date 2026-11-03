package vn.dxlab.recruitment.clerk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.config.ClerkProperties;

import java.util.Map;

/**
 * Gửi lời mời đăng ký qua Clerk Backend API ({@code POST /v1/invitations}).
 * Sign-up trên Clerk để chế độ Restricted, nên đây là cách duy nhất để nhân sự mới tạo được tài khoản.
 */
@Component
public class ClerkInvitationClient {

    private static final Logger log = LoggerFactory.getLogger(ClerkInvitationClient.class);

    private final RestClient http;
    private final ClerkProperties clerk;

    public ClerkInvitationClient(ClerkProperties clerk) {
        this.clerk = clerk;
        this.http = RestClient.builder().baseUrl(clerk.apiUrl()).build();
    }

    /**
     * @param redirectUrl trang frontend mở ra sau khi người được mời bấm link trong email
     */
    public void invite(String email, String redirectUrl) {
        if (clerk.secretKey() == null || clerk.secretKey().isBlank()) {
            throw new BusinessException("CLERK_NOT_CONFIGURED", "Chưa cấu hình CLERK_SECRET_KEY nên không gửi được lời mời");
        }
        try {
            http.post()
                    .uri("/v1/invitations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + clerk.secretKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email_address", email,
                            "redirect_url", redirectUrl,
                            "notify", true,
                            "ignore_existing", true))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Đã gửi lời mời Clerk tới {}", email);
        } catch (RestClientException e) {
            log.warn("Gửi lời mời Clerk tới {} thất bại: {}", email, e.getMessage());
            throw new BusinessException("CLERK_INVITE_FAILED", "Không gửi được lời mời qua Clerk. Kiểm tra lại cấu hình hoặc thử lại sau.");
        }
    }
}
