package vn.dxlab.recruitment.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.dxlab.recruitment.security.UserIdentityService;

import java.time.Duration;

/**
 * Cache ánh xạ Clerk user → tài khoản trong 60 giây, để mỗi request không phải truy vấn DB.
 * Đổi vai trò hay khóa tài khoản thì gọi {@link UserIdentityService#evictAll()}.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(UserIdentityService.CACHE);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).maximumSize(500));
        return manager;
    }
}
