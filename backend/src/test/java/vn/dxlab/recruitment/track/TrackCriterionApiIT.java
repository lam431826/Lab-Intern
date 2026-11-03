package vn.dxlab.recruitment.track;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.scoring.Score;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TrackCriterionApiIT extends ApiIntegrationTest {

    @Autowired TrackRepository tracks;
    @Autowired CriterionRepository criteria;
    @Autowired CandidateRepository candidates;
    @Autowired ScoreRepository scores;

    @Test
    void everyRoleCanReadTracksAndRubrics() {
        AppUser member = createLinkedUser("user_t1", "t1@dxlab.vn", Role.MEMBER);
        Long se = tracks.findByCode("SE").orElseThrow().getId();

        assertThat(mvc.get().uri("/api/tracks").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(6);
        assertThat(mvc.get().uri("/api/criteria?trackId=" + se).header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(9);
    }

    @Test
    void organizerAssignsLeadButOnlyToLeadOrOrganizer() {
        AppUser organizer = createLinkedUser("user_t2", "t2@dxlab.vn", Role.ORGANIZER);
        AppUser lead = createUser("lead.ia@dxlab.vn", "Nguyễn Quý Hòa", Role.LEAD);
        AppUser member = createUser("mem.ia@dxlab.vn", "Thành viên", Role.MEMBER);
        Long ia = tracks.findByCode("IA").orElseThrow().getId();

        assertThat(mvc.put().uri("/api/tracks/" + ia).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(trackJson(lead.getId())))
                .hasStatusOk().bodyJson().extractingPath("$.leadName").isEqualTo("Nguyễn Quý Hòa");

        assertThat(mvc.put().uri("/api/tracks/" + ia).header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(trackJson(member.getId())))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_TRACK_LEAD");
    }

    @Test
    void leadManagesOnlyOwnTrackCriteria() {
        AppUser lead = createLinkedUser("user_t3", "t3@dxlab.vn", Role.LEAD);
        Track graphic = tracks.findByCode("GRAPHIC").orElseThrow();
        graphic.setLead(lead);
        Long se = tracks.findByCode("SE").orElseThrow().getId();

        assertThat(mvc.post().uri("/api/criteria").header(HttpHeaders.AUTHORIZATION, bearer(lead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(criterionJson(graphic.getId(), "Bố cục & typography")))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/criteria").header(HttpHeaders.AUTHORIZATION, bearer(lead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(criterionJson(se, "Không phải ngạch mình")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/criteria").header(HttpHeaders.AUTHORIZATION, bearer(lead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(criterionJson(null, "Tiêu chí chung mới")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void scoredCriterionIsHiddenInsteadOfDeleted() {
        AppUser organizer = createLinkedUser("user_t4", "t4@dxlab.vn", Role.ORGANIZER);
        Track ai = tracks.findByCode("AI").orElseThrow();
        Criterion used = newCriterion(ai, "Đã chấm");
        Criterion unused = newCriterion(ai, "Chưa chấm");

        Candidate c = new Candidate();
        c.setStudentCode("AI7001");
        c.setFullName("Ứng viên mẫu");
        c.setTrack(ai);
        candidates.saveAndFlush(c);
        Score s = new Score();
        s.setCandidate(c);
        s.setEvaluator(organizer);
        s.setCriterion(used);
        s.setValue(8);
        scores.saveAndFlush(s);

        assertThat(mvc.delete().uri("/api/criteria/" + used.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/criteria/" + unused.getId()).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.NO_CONTENT);

        criteria.flush();
        assertThat(criteria.findById(used.getId())).get().extracting(Criterion::isActive).isEqualTo(false);
        assertThat(criteria.findById(unused.getId())).isEmpty();
    }

    @Test
    void settingsRequireWeightsSummingToOne() {
        AppUser lecturer = createLinkedUser("user_t5", "t5@dxlab.vn", Role.LECTURER);
        AppUser member = createLinkedUser("user_t6", "t6@dxlab.vn", Role.MEMBER);

        assertThat(mvc.put().uri("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer(lecturer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson("0.50", "0.60")))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_WEIGHTS");

        assertThat(mvc.put().uri("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer(lecturer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson("0.30", "0.70")))
                .hasStatusOk().bodyJson().extractingPath("$.weightProfessional").isEqualTo(0.70);

        assertThat(mvc.get().uri("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer(member))).hasStatusOk();
        assertThat(mvc.put().uri("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson("0.30", "0.70")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private Criterion newCriterion(Track track, String name) {
        Criterion c = new Criterion();
        c.setTrack(track);
        c.setGroup(CriterionGroup.PROFESSIONAL);
        c.setName(name);
        c.setSortOrder(99);
        return criteria.saveAndFlush(c);
    }

    private static String trackJson(Long leadId) {
        return """
                {"name":"IA (Information Assurance)","quota":3,"slotMin":4,"slotMax":6,"requiresPortfolio":false,"leadUserId":%d}
                """.formatted(leadId);
    }

    private static String criterionJson(Long trackId, String name) {
        return """
                {"trackId":%s,"name":"%s","rubricLow":"Thấp","rubricMid":"Khá","rubricHigh":"Tốt","sortOrder":10}
                """.formatted(trackId == null ? "null" : trackId, name);
    }

    private static String settingsJson(String general, String professional) {
        return """
                {"weightGeneral":%s,"weightProfessional":%s,"passThreshold":7.0,"slaHours":36,"slotMinutes":15}
                """.formatted(general, professional);
    }
}
