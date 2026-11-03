package vn.dxlab.recruitment.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;
import vn.dxlab.recruitment.common.error.ApiError;
import vn.dxlab.recruitment.security.ClerkJwtAuthenticationConverter;
import vn.dxlab.recruitment.user.Role;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * API stateless dùng Bearer token của Clerk, nên không cần session hay CSRF token.
 * Mọi đường dẫn /api/** bắt buộc có vai trò; quyền chi tiết (theo ngạch, theo ca)
 * kiểm tra bằng {@code @PreAuthorize} ở controller và kiểm tra lại trong service.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    static final String WEBHOOK_PATH = "/api/webhooks/clerk";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ClerkJwtAuthenticationConverter converter,
                                            JsonMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, e) -> write(json, res, HttpServletResponse.SC_UNAUTHORIZED,
                new ApiError("UNAUTHORIZED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."));
        AccessDeniedHandler forbidden = (req, res, e) -> write(json, res, HttpServletResponse.SC_FORBIDDEN,
                new ApiError("FORBIDDEN", "Tài khoản chưa được cấp quyền thực hiện thao tác này."));

        String[] allRoles = Arrays.stream(Role.values()).map(Role::name).toArray(String[]::new);

        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, WEBHOOK_PATH).permitAll()
                        .requestMatchers("/api/**").hasAnyRole(allRoles)
                        // Còn lại là giao diện React (file tĩnh), tự xử lý đăng nhập bằng Clerk
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Content-Disposition"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static void write(JsonMapper json, HttpServletResponse res, int status, ApiError body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(json.writeValueAsString(body));
    }
}
