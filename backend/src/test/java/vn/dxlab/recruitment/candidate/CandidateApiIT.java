package vn.dxlab.recruitment.candidate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.interview.InterviewSession;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.interview.InterviewSlot;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CandidateApiIT extends ApiIntegrationTest {

    @Autowired TrackRepository tracks;
    @Autowired CandidateRepository candidates;
    @Autowired InterviewSessionRepository sessions;
    @Autowired InterviewSlotRepository slots;

    AppUser organizer;
    AppUser graphicLead;
    Track graphic;
    Track se;

    @BeforeEach
    void setUp() {
        organizer = createLinkedUser("user_c_org", "c.org@dxlab.vn", Role.ORGANIZER);
        graphicLead = createLinkedUser("user_c_lead", "c.lead@dxlab.vn", Role.LEAD);
        graphic = tracks.findByCode("GRAPHIC").orElseThrow();
        graphic.setLead(graphicLead);
        se = tracks.findByCode("SE").orElseThrow();
    }

    @Test
    void leadSeesOnlyOwnTrackAndSearchIgnoresAccents() {
        candidate("GD001", "Nguyễn Thị Hồng", graphic);
        candidate("GD002", "Trần Văn Nam", graphic);
        candidate("SE001", "Nguyễn Văn Sơn", se);

        assertThat(mvc.get().uri("/api/candidates").header(HttpHeaders.AUTHORIZATION, bearer(graphicLead)))
                .hasStatusOk().bodyJson().extractingPath("$[*].studentCode").asArray().containsExactly("GD001", "GD002");
        assertThat(mvc.get().uri("/api/candidates?q=nguyen").header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatusOk().bodyJson().extractingPath("$[*].studentCode").asArray().containsExactlyInAnyOrder("GD001", "SE001");
        assertThat(mvc.get().uri("/api/candidates?trackId=" + se.getId()).header(HttpHeaders.AUTHORIZATION, bearer(graphicLead)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberCannotListCandidates() {
        AppUser member = createLinkedUser("user_c_mem", "c.mem@dxlab.vn", Role.MEMBER);
        assertThat(mvc.get().uri("/api/candidates").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void flagsMissingPortfolioAndOverdueProfiles() {
        Candidate c = candidate("GD010", "Lê Minh", graphic);
        c.setCvLink("https://drive.google.com/cv");
        c.setAssignedAt(Instant.now().minus(Duration.ofHours(40)));
        candidates.saveAndFlush(c);

        assertThat(mvc.get().uri("/api/candidates/" + c.getId()).header(HttpHeaders.AUTHORIZATION, bearer(graphicLead)))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.flags").asArray().containsExactly("MISSING_PORTFOLIO");
                    json.assertThat().extractingPath("$.overdue").isEqualTo(true);
                });
        assertThat(mvc.get().uri("/api/candidates/summary").header(HttpHeaders.AUTHORIZATION, bearer(graphicLead)))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.length()").isEqualTo(1);
                    json.assertThat().extractingPath("$[0].overdue").isEqualTo(1);
                });
    }

    @Test
    void leadScreensOwnTrackAndHistoryIsKept() {
        Candidate c = candidate("GD020", "Phạm Thu", graphic);

        assertThat(mvc.put().uri("/api/candidates/" + c.getId() + "/screening")
                .header(HttpHeaders.AUTHORIZATION, bearer(graphicLead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(screening("PASSED", null, c.getVersion())))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.screeningStatus").isEqualTo("PASSED");
                    json.assertThat().extractingPath("$.cvAverage").isEqualTo(7.8);
                    json.assertThat().extractingPath("$.screenedByName").isEqualTo("c.lead@dxlab.vn");
                });
        assertThat(mvc.get().uri("/api/candidates/" + c.getId() + "/history").header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$[-1:].newValue").asArray().containsExactly("PASSED");
                    json.assertThat().extractingPath("$[-1:].changedByName").asArray().containsExactly("c.lead@dxlab.vn");
                });
    }

    @Test
    void rejectingRequiresReason() {
        Candidate c = candidate("GD030", "Hồ Lan", graphic);

        assertThat(mvc.put().uri("/api/candidates/" + c.getId() + "/screening")
                .header(HttpHeaders.AUTHORIZATION, bearer(graphicLead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(screening("REJECTED", "  ", c.getVersion())))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("REASON_REQUIRED");
    }

    @Test
    void leadCannotScreenAnotherTrack() {
        Candidate c = candidate("SE030", "Võ Hải", se);

        assertThat(mvc.put().uri("/api/candidates/" + c.getId() + "/screening")
                .header(HttpHeaders.AUTHORIZATION, bearer(graphicLead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(screening("PASSED", null, c.getVersion())))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void detectsConcurrentScreening() {
        Candidate c = candidate("GD040", "Đỗ An", graphic);
        int openedVersion = c.getVersion();

        assertThat(mvc.put().uri("/api/candidates/" + c.getId() + "/screening")
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(screening("PASSED", null, openedVersion)))
                .hasStatusOk();
        assertThat(mvc.put().uri("/api/candidates/" + c.getId() + "/screening")
                .header(HttpHeaders.AUTHORIZATION, bearer(graphicLead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(screening("REJECTED", "Portfolio chưa đạt", openedVersion)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("STALE_DATA");
    }

    @Test
    void scheduledCandidateKeepsPassedStatusAndCannotBeDeleted() {
        Candidate c = candidate("GD050", "Mai Linh", graphic);
        c.setScreeningStatus(ScreeningStatus.PASSED);
        candidates.saveAndFlush(c);
        schedule(c);

        assertThat(mvc.put().uri("/api/candidates/" + c.getId() + "/screening")
                .header(HttpHeaders.AUTHORIZATION, bearer(graphicLead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(screening("REJECTED", "Đổi ý", c.getVersion())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CANDIDATE_SCHEDULED");
        assertThat(mvc.delete().uri("/api/candidates/" + c.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CANDIDATE_IN_USE");
    }

    @Test
    void changingTrackResetsScreeningForNewLead() {
        Candidate c = candidate("GD060", "Quang Huy", graphic);
        c.setScreeningStatus(ScreeningStatus.PASSED);
        candidates.saveAndFlush(c);

        assertThat(mvc.put().uri("/api/candidates/" + c.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentCode":"gd060","fullName":"Quang Huy","trackId":%d}
                        """.formatted(se.getId())))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.trackCode").isEqualTo("SE");
                    json.assertThat().extractingPath("$.screeningStatus").isEqualTo("PENDING");
                    json.assertThat().extractingPath("$.studentCode").isEqualTo("GD060");
                });
    }

    private Candidate candidate(String code, String name, Track track) {
        Candidate c = new Candidate();
        c.setStudentCode(code);
        c.setFullName(name);
        c.setTrack(track);
        return candidates.saveAndFlush(c);
    }

    private void schedule(Candidate c) {
        InterviewSession s = new InterviewSession();
        s.setTrack(c.getTrack());
        s.setSessionDate(LocalDate.of(2026, 8, 10));
        s.setStartTime(LocalTime.of(11, 0));
        s.setEndTime(LocalTime.of(12, 30));
        sessions.saveAndFlush(s);
        InterviewSlot slot = new InterviewSlot();
        slot.setSession(s);
        slot.setCandidate(c);
        slot.setStartTime(LocalTime.of(11, 0));
        slot.setEndTime(LocalTime.of(11, 15));
        slots.saveAndFlush(slot);
    }

    private static String screening(String status, String reason, int version) {
        return """
                {"cvPersonalInfo":7,"cvExperience":8,"cvSkills":8,"cvProjects":8,"cvCertificates":8,
                 "status":"%s","reason":%s,"version":%d}
                """.formatted(status, reason == null ? "null" : "\"" + reason + "\"", version);
    }
}
