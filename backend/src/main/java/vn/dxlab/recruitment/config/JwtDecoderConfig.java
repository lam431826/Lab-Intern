package vn.dxlab.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import vn.dxlab.recruitment.security.AzpValidator;

import java.time.Duration;

/**
 * Xác minh session token của Clerk: chữ ký RS256 theo JWKS của instance, hạn dùng
 * (cho lệch đồng hồ 30 giây vì token Clerk chỉ sống 60 giây), issuer và azp.
 * JWKS được tải khi có request đầu tiên và được cache, nên khởi động không cần mạng.
 */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtDecoder jwtDecoder(ClerkProperties clerk) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(clerk.jwksUri()).build();
        decoder.setJwtValidator(clerkTokenValidator(clerk));
        return decoder;
    }

    /** Dùng chung cho decoder thật và decoder trong test. */
    public static OAuth2TokenValidator<Jwt> clerkTokenValidator(ClerkProperties clerk) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(clerk.normalizedIssuer()),
                new AzpValidator(clerk.authorizedParties()));
    }
}
