package vn.dxlab.recruitment.clerk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ClerkWebhookIT extends ApiIntegrationTest {

    @Autowired SvixSignatureVerifier verifier;
    @Autowired ProcessedWebhookRepository processed;

    @Test
    void rejectsUnsignedWebhook() {
        assertThat(mvc.post().uri("/api/webhooks/clerk")
                .contentType(MediaType.APPLICATION_JSON)
                .header("svix-id", "msg_unsigned")
                .header("svix-timestamp", now())
                .header("svix-signature", "v1,AAAA")
                .content("{\"type\":\"user.deleted\",\"data\":{\"id\":\"user_x\"}}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    void userDeletedOnClerkDeactivatesAccountOnce() {
        AppUser u = createLinkedUser("user_gone", "gone@dxlab.vn", Role.MEMBER);
        String body = "{\"type\":\"user.deleted\",\"data\":{\"id\":\"user_gone\",\"deleted\":true}}";

        assertThat(send("msg_delete_1", body)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(users.findById(u.getId()).orElseThrow().isActive()).isFalse();

        // Svix gửi lại cùng sự kiện: không lỗi, không xử lý lần hai
        assertThat(send("msg_delete_1", body)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void userUpdatedSyncsPrimaryEmail() {
        AppUser u = createLinkedUser("user_mail", "old.mail@dxlab.vn", Role.MEMBER);
        String body = """
                {"type":"user.updated","data":{"id":"user_mail","primary_email_address_id":"idn_2",
                 "email_addresses":[{"id":"idn_1","email_address":"old.mail@dxlab.vn"},
                                    {"id":"idn_2","email_address":"new.mail@dxlab.vn"}]}}
                """;

        assertThat(send("msg_update_1", body)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(users.findById(u.getId()).orElseThrow().getEmail()).isEqualTo("new.mail@dxlab.vn");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult send(String svixId, String body) {
        String ts = now();
        return mvc.post().uri("/api/webhooks/clerk")
                .contentType(MediaType.APPLICATION_JSON)
                .header("svix-id", svixId)
                .header("svix-timestamp", ts)
                .header("svix-signature", verifier.signatureFor(svixId, ts, body))
                .content(body)
                .exchange();
    }

    private static String now() {
        return String.valueOf(Instant.now().getEpochSecond());
    }
}
