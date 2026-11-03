package vn.dxlab.recruitment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Cấu hình Clerk.
 *
 * @param issuer            Frontend API URL của instance Clerk, ví dụ https://xxx.clerk.accounts.dev
 * @param authorizedParties origin của frontend được phép; so với claim {@code azp} của token
 * @param secretKey         khóa Backend API, dùng để gửi lời mời
 * @param webhookSecret     khóa ký webhook dạng {@code whsec_...}
 * @param apiUrl            địa chỉ Backend API của Clerk
 * @param webhookTolerance  độ lệch thời gian tối đa của webhook, chống gửi lại gói tin cũ
 */
@ConfigurationProperties("app.clerk")
public record ClerkProperties(
        String issuer,
        List<String> authorizedParties,
        String secretKey,
        String webhookSecret,
        @DefaultValue("https://api.clerk.com") String apiUrl,
        @DefaultValue("PT5M") Duration webhookTolerance) {

    public String jwksUri() {
        return trimTrailingSlash(issuer) + "/.well-known/jwks.json";
    }

    public String normalizedIssuer() {
        return trimTrailingSlash(issuer);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình CLERK_ISSUER (Frontend API URL của Clerk)");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
