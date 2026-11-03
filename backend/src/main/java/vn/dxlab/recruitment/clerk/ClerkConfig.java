package vn.dxlab.recruitment.clerk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import vn.dxlab.recruitment.config.ClerkProperties;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClerkConfig {

    /** Lazy: thiếu CLERK_WEBHOOK_SECRET thì chỉ webhook lỗi, ứng dụng vẫn khởi động được. */
    @Bean
    @Lazy
    SvixSignatureVerifier svixSignatureVerifier(ClerkProperties clerk) {
        return new SvixSignatureVerifier(clerk.webhookSecret(), clerk.webhookTolerance(), Clock.systemUTC());
    }
}
