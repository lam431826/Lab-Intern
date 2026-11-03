package vn.dxlab.recruitment.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** Phiên đã xác thực: token Clerk hợp lệ và đã khớp với một tài khoản đang hoạt động. */
public class AppUserAuthentication extends AbstractAuthenticationToken {

    private final AppUserPrincipal principal;
    private final transient Jwt jwt;

    public AppUserAuthentication(AppUserPrincipal principal, Jwt jwt) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public AppUserPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.email();
    }
}
