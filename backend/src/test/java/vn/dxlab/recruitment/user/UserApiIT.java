package vn.dxlab.recruitment.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Transactional
class UserApiIT extends ApiIntegrationTest {

    @Autowired TrackRepository tracks;

    @Test
    void organizerCreatesAccountAndSendsClerkInvite() {
        AppUser organizer = createLinkedUser("user_org1", "org1@dxlab.vn", Role.ORGANIZER);

        assertThat(mvc.post().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"  Quy.Hoa@DXLab.vn ","fullName":"Nguyễn Quý Hòa","role":"LEAD"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.email").isEqualTo("quy.hoa@dxlab.vn");
                    json.assertThat().extractingPath("$.linked").isEqualTo(false);
                });
        verify(invitations).invite(eq("quy.hoa@dxlab.vn"), anyString());
    }

    @Test
    void canSkipInvite() {
        AppUser organizer = createLinkedUser("user_org2", "org2@dxlab.vn", Role.ORGANIZER);

        assertThat(mvc.post().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"later@dxlab.vn\",\"fullName\":\"Mời sau\",\"role\":\"MEMBER\",\"sendInvite\":false}"))
                .hasStatus(HttpStatus.CREATED);
        verify(invitations, never()).invite(anyString(), anyString());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        AppUser organizer = createLinkedUser("user_org3", "org3@dxlab.vn", Role.ORGANIZER);
        createUser("dup@dxlab.vn", "Đã có", Role.MEMBER);

        assertThat(mvc.post().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"DUP@dxlab.vn\",\"fullName\":\"Trùng\",\"role\":\"MEMBER\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("USER_EMAIL_EXISTS");
    }

    @Test
    void validatesInput() {
        AppUser organizer = createLinkedUser("user_org4", "org4@dxlab.vn", Role.ORGANIZER);

        assertThat(mvc.post().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"khong-phai-email\",\"fullName\":\"\",\"role\":null}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors.length()").isEqualTo(3);
                });
    }

    @Test
    void lecturerCanListButNotCreate() {
        AppUser lecturer = createLinkedUser("user_lec", "lec@dxlab.vn", Role.LECTURER);

        assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(lecturer))).hasStatusOk();
        assertThat(mvc.post().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(lecturer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@dxlab.vn\",\"fullName\":\"X\",\"role\":\"MEMBER\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberCannotListAccounts() {
        AppUser member = createLinkedUser("user_mem", "mem@dxlab.vn", Role.MEMBER);

        assertThat(mvc.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void organizerCannotLockThemselves() {
        AppUser organizer = createLinkedUser("user_self", "self@dxlab.vn", Role.ORGANIZER);

        assertThat(mvc.put().uri("/api/users/" + organizer.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Tôi\",\"role\":\"ORGANIZER\",\"active\":false}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("CANNOT_CHANGE_SELF");
    }

    @Test
    void roleChangeAndLockTakeEffectOnNextRequest() {
        AppUser organizer = createLinkedUser("user_org5", "org5@dxlab.vn", Role.ORGANIZER);
        AppUser member = createLinkedUser("user_m5", "m5@dxlab.vn", Role.MEMBER);
        String memberToken = bearer(member);

        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, memberToken))
                .bodyJson().extractingPath("$.role").isEqualTo("MEMBER");

        assertThat(mvc.put().uri("/api/users/" + member.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Thành viên\",\"role\":\"LEAD\",\"active\":true}"))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, memberToken))
                .bodyJson().extractingPath("$.role").isEqualTo("LEAD");

        assertThat(mvc.put().uri("/api/users/" + member.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Thành viên\",\"role\":\"LEAD\",\"active\":false}"))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, memberToken))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void trackLeadCannotBeDemotedWhileAssigned() {
        AppUser organizer = createLinkedUser("user_org6", "org6@dxlab.vn", Role.ORGANIZER);
        AppUser lead = createUser("lead6@dxlab.vn", "Lead IA", Role.LEAD);
        Track ia = tracks.findByCode("IA").orElseThrow();
        ia.setLead(lead);
        tracks.saveAndFlush(ia);

        assertThat(mvc.put().uri("/api/users/" + lead.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Lead IA\",\"role\":\"MEMBER\",\"active\":true}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("USER_IS_TRACK_LEAD");
    }

    @Test
    void alreadyLinkedAccountNeedsNoInvite() {
        AppUser organizer = createLinkedUser("user_org7", "org7@dxlab.vn", Role.ORGANIZER);
        AppUser member = createLinkedUser("user_m7", "m7@dxlab.vn", Role.MEMBER);

        assertThat(mvc.post().uri("/api/users/" + member.getId() + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("ALREADY_LINKED");
    }

    /** Không chạy trong transaction của test, để thấy được rollback thật của service. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedInviteDoesNotLeaveAccountBehind() {
        AppUser organizer = createLinkedUser("user_org8", "org8@dxlab.vn", Role.ORGANIZER);
        doThrow(new BusinessException("CLERK_INVITE_FAILED", "Clerk lỗi"))
                .when(invitations).invite(eq("fail@dxlab.vn"), anyString());
        try {
            assertThat(mvc.post().uri("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"fail@dxlab.vn\",\"fullName\":\"Không mời được\",\"role\":\"MEMBER\"}"))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("CLERK_INVITE_FAILED");
            assertThat(users.existsByEmail("fail@dxlab.vn")).isFalse();
        } finally {
            users.delete(organizer);
        }
    }
}
