package vn.dxlab.recruitment.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.dxlab.recruitment.clerk.ClerkInvitationClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;
import vn.dxlab.recruitment.user.Role;

/**
 * Nền cho test gọi API qua toàn bộ filter chain bảo mật, trên SQL Server thật.
 * JwtDecoder của Clerk (tải JWKS qua mạng) được thay bằng decoder dùng khóa test.
 */
@IntegrationTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

    @TestBean
    JwtDecoder jwtDecoder;

    /** Không gọi Clerk thật trong test; test tự kiểm tra lời mời có được gửi hay không. */
    @MockitoBean
    protected ClerkInvitationClient invitations;

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected AppUserRepository users;

    @Autowired
    private CacheManager cacheManager;

    static JwtDecoder jwtDecoder() {
        return JwtFixtures.decoder();
    }

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    /** Tạo tài khoản như Người tổ chức tạo sẵn: có email + vai trò, chưa liên kết Clerk. */
    protected AppUser createUser(String email, String fullName, Role role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    /** Tài khoản đã liên kết với một Clerk user. */
    protected AppUser createLinkedUser(String clerkUserId, String email, Role role) {
        AppUser u = new AppUser();
        u.setClerkUserId(clerkUserId);
        u.setEmail(email);
        u.setFullName(email);
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    /** Header Authorization cho một tài khoản đã liên kết. */
    protected static String bearer(AppUser u) {
        return JwtFixtures.token(u.getClerkUserId()).bearer();
    }
}
