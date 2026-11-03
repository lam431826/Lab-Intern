package vn.dxlab.recruitment.clerk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Xác minh webhook Clerk theo chuẩn Svix:
 * chữ ký = base64(HMAC-SHA256(secret, "{svix-id}.{svix-timestamp}.{body}")),
 * header {@code svix-signature} chứa một hoặc nhiều mục {@code v1,<chữ ký>} cách nhau bởi dấu cách.
 * Từ chối gói tin lệch thời gian quá {@code tolerance} để chặn gửi lại gói tin cũ.
 */
public class SvixSignatureVerifier {

    private static final String SECRET_PREFIX = "whsec_";

    private final byte[] key;
    private final Duration tolerance;
    private final Clock clock;

    public SvixSignatureVerifier(String secret, Duration tolerance, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình CLERK_WEBHOOK_SECRET");
        }
        String raw = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        this.key = Base64.getDecoder().decode(raw);
        this.tolerance = tolerance;
        this.clock = clock;
    }

    public boolean verify(String svixId, String svixTimestamp, String signatureHeader, String body) {
        if (svixId == null || svixTimestamp == null || signatureHeader == null || body == null) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(svixTimestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        Duration skew = Duration.between(Instant.ofEpochSecond(seconds), clock.instant()).abs();
        if (skew.compareTo(tolerance) > 0) {
            return false;
        }
        byte[] expected = sign(svixId + "." + svixTimestamp.trim() + "." + body);
        for (String part : signatureHeader.trim().split(" ")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !"v1".equals(part.substring(0, comma))) {
                continue;
            }
            byte[] given;
            try {
                given = Base64.getDecoder().decode(part.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) {
                return true;
            }
        }
        return false;
    }

    /** Dùng trong test để tạo chữ ký hợp lệ. */
    public String signatureFor(String svixId, String svixTimestamp, String body) {
        return "v1," + Base64.getEncoder().encodeToString(sign(svixId + "." + svixTimestamp + "." + body));
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không khởi tạo được HMAC-SHA256", e);
        }
    }
}
