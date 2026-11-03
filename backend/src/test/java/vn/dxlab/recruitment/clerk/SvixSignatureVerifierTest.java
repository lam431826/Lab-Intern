package vn.dxlab.recruitment.clerk;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SvixSignatureVerifierTest {

    private static final String SECRET = "whsec_ZHhsYWItd2ViaG9vay10ZXN0LXNlY3JldC0wMTIzNDU2Nzg5";
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    private static final String BODY = "{\"type\":\"user.deleted\",\"data\":{\"id\":\"user_1\"}}";

    private final SvixSignatureVerifier verifier =
            new SvixSignatureVerifier(SECRET, Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidSignature() {
        String sig = verifier.signatureFor("msg_1", TS, BODY);
        assertThat(verifier.verify("msg_1", TS, sig, BODY)).isTrue();
    }

    @Test
    void acceptsWhenOneOfSeveralSignaturesMatches() {
        String sig = "v1,AAAA " + verifier.signatureFor("msg_1", TS, BODY);
        assertThat(verifier.verify("msg_1", TS, sig, BODY)).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        String sig = verifier.signatureFor("msg_1", TS, BODY);
        assertThat(verifier.verify("msg_1", TS, sig, BODY.replace("user_1", "user_2"))).isFalse();
    }

    @Test
    void rejectsSignatureFromAnotherMessageId() {
        String sig = verifier.signatureFor("msg_1", TS, BODY);
        assertThat(verifier.verify("msg_2", TS, sig, BODY)).isFalse();
    }

    @Test
    void rejectsReplayOutsideTolerance() {
        String oldTs = String.valueOf(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        String sig = verifier.signatureFor("msg_1", oldTs, BODY);
        assertThat(verifier.verify("msg_1", oldTs, sig, BODY)).isFalse();
    }

    @Test
    void rejectsMissingHeadersAndGarbage() {
        assertThat(verifier.verify(null, TS, "v1,abc", BODY)).isFalse();
        assertThat(verifier.verify("msg_1", "not-a-number", "v1,abc", BODY)).isFalse();
        assertThat(verifier.verify("msg_1", TS, "v1,!!!notbase64", BODY)).isFalse();
        assertThat(verifier.verify("msg_1", TS, "v2," + verifier.signatureFor("msg_1", TS, BODY).substring(3), BODY)).isFalse();
    }
}
