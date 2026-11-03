package vn.dxlab.recruitment.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import vn.dxlab.recruitment.config.ClerkProperties;
import vn.dxlab.recruitment.config.JwtDecoderConfig;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Tạo session token giống Clerk (RS256, claim sub/iss/azp/sid/email) bằng khóa RSA sinh lúc chạy test.
 * Decoder test dùng khóa công khai tương ứng và CÙNG bộ kiểm tra issuer/thời gian/azp với môi trường thật.
 */
public final class JwtFixtures {

    public static final String ISSUER = "https://test.clerk.local";
    public static final String AZP = "http://localhost:5173";

    private static final KeyPair KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();

    private JwtFixtures() {
    }

    public static JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        decoder.setJwtValidator(JwtDecoderConfig.clerkTokenValidator(
                new ClerkProperties(ISSUER, List.of(AZP), null, null, null, null)));
        return decoder;
    }

    public static Builder token(String clerkUserId) {
        return new Builder(clerkUserId);
    }

    public static final class Builder {
        private final String sub;
        private String email;
        private String issuer = ISSUER;
        private String azp = AZP;
        private Instant issuedAt = Instant.now();
        private Duration ttl = Duration.ofSeconds(60);
        private KeyPair keys = KEYS;

        private Builder(String sub) {
            this.sub = sub;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder azp(String azp) {
            this.azp = azp;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        /** Ký bằng khóa khác khóa decoder tin cậy, mô phỏng token giả mạo. */
        public Builder signedByUnknownKey() {
            this.keys = OTHER_KEYS;
            return this;
        }

        public String build() {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(sub)
                    .issuer(issuer)
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(Date.from(issuedAt))
                    .expirationTime(Date.from(issuedAt.plus(ttl)))
                    .claim("sid", "sess_" + UUID.randomUUID());
            if (azp != null) {
                claims.claim("azp", azp);
            }
            if (email != null) {
                claims.claim("email", email);
            }
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims.build());
                jwt.sign(new RSASSASigner(keys.getPrivate()));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }

        public String bearer() {
            return "Bearer " + build();
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
