package vn.dxlab.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.dxlab.recruitment.security.AppUserPrincipal;

import java.util.Optional;

/** Tự điền created_by / updated_by bằng id người dùng đang đăng nhập. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "currentAuditor")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<Long> currentAuditor() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(auth -> auth.getPrincipal() instanceof AppUserPrincipal p ? p.id() : null);
    }
}
