package vn.dxlab.recruitment.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.support.JwtFixtures;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Xác thực token Clerk và liên kết tài khoản lần đầu, đi qua toàn bộ filter chain. */
@Transactional
class AuthenticationIT extends ApiIntegrationTest {

    @Test
    void rejectsRequestWithoutToken() {
        assertThat(mvc.get().uri("/api/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void healthCheckIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void rejectsTokenSignedByUnknownKey() {
        createLinkedUser("user_forged", "forged@dxlab.vn", Role.ORGANIZER);
        String token = JwtFixtures.token("user_forged").signedByUnknownKey().bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTokenFromAnotherOrigin() {
        createLinkedUser("user_azp", "azp@dxlab.vn", Role.ORGANIZER);
        String token = JwtFixtures.token("user_azp").azp("https://evil.example").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTokenWithoutAzp() {
        createLinkedUser("user_noazp", "noazp@dxlab.vn", Role.ORGANIZER);
        String token = JwtFixtures.token("user_noazp").azp(null).bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTokenFromAnotherClerkInstance() {
        createLinkedUser("user_iss", "iss@dxlab.vn", Role.ORGANIZER);
        String token = JwtFixtures.token("user_iss").issuer("https://other.clerk.accounts.dev").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsExpiredToken() {
        createLinkedUser("user_old", "old@dxlab.vn", Role.ORGANIZER);
        String token = JwtFixtures.token("user_old").issuedAt(Instant.now().minus(Duration.ofMinutes(5))).bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validClerkUserWithoutAccountIsForbidden() {
        String token = JwtFixtures.token("user_stranger").email("stranger@gmail.com").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void firstLoginLinksPreCreatedAccountByEmail() {
        AppUser lead = createUser("lead.ai@dxlab.vn", "Bùi Trần Phú", Role.LEAD);
        String token = JwtFixtures.token("user_lead_ai").email("LEAD.AI@dxlab.vn").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.fullName").isEqualTo("Bùi Trần Phú");
                    json.assertThat().extractingPath("$.role").isEqualTo("LEAD");
                });
        assertThat(users.findById(lead.getId()).orElseThrow().getClerkUserId()).isEqualTo("user_lead_ai");
    }

    @Test
    void linkedAccountDoesNotNeedEmailClaim() {
        createLinkedUser("user_linked", "linked@dxlab.vn", Role.MEMBER);
        String token = JwtFixtures.token("user_linked").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatusOk()
                .bodyJson().extractingPath("$.role").isEqualTo("MEMBER");
    }

    @Test
    void emailAlreadyLinkedToAnotherClerkUserIsNotTakenOver() {
        createLinkedUser("user_owner", "owner@dxlab.vn", Role.ORGANIZER);
        String token = JwtFixtures.token("user_intruder").email("owner@dxlab.vn").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void deactivatedAccountIsForbidden() {
        AppUser u = createLinkedUser("user_locked", "locked@dxlab.vn", Role.ORGANIZER);
        u.setActive(false);
        users.saveAndFlush(u);
        String token = JwtFixtures.token("user_locked").bearer();

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
