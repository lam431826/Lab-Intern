package vn.dxlab.recruitment.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token hợp lệ nhưng chưa khớp tài khoản nào (hoặc tài khoản bị khóa) vẫn được xác thực,
 * nhưng không có vai trò nào, nên mọi API yêu cầu vai trò sẽ trả 403 thay vì 401.
 * Như vậy frontend phân biệt được "chưa đăng nhập" với "chưa được cấp quyền".
 */
@Component
public class ClerkJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String EMAIL_CLAIM = "email";

    private final UserIdentityService identities;

    public ClerkJwtAuthenticationConverter(UserIdentityService identities) {
        this.identities = identities;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUserPrincipal principal = identities.resolve(jwt.getSubject(), jwt.getClaimAsString(EMAIL_CLAIM));
        return principal != null
                ? new AppUserAuthentication(principal, jwt)
                : new JwtAuthenticationToken(jwt, List.of());
    }
}
