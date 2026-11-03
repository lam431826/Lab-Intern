package vn.dxlab.recruitment.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;

/**
 * Clerk ghi origin của trang đã xin token vào claim {@code azp}. Chỉ chấp nhận token xin từ
 * frontend của hệ thống, để token lấy từ một trang khác dùng cùng instance Clerk không gọi được API.
 */
public class AzpValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AZP =
            new OAuth2Error("invalid_token", "Token không được cấp cho ứng dụng này (azp)", null);

    private final Set<String> authorizedParties;

    public AzpValidator(List<String> authorizedParties) {
        this.authorizedParties = Set.copyOf(authorizedParties == null ? List.of() : authorizedParties);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        return azp != null && authorizedParties.contains(azp)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AZP);
    }
}
