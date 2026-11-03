package vn.dxlab.recruitment.scoring;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.candidate.ScreeningStatus;
import vn.dxlab.recruitment.result.Decision;
import vn.dxlab.recruitment.result.DecisionRepository;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.track.CriterionRepository;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ScoringApiIT extends ApiIntegrationTest {

    @Autowired TrackRepository tracks;
    @Autowired CandidateRepository candidates;
    @Autowired CriterionRepository criteria;
    @Autowired DecisionRepository decisions;

    AppUser organizer;
    AppUser judgeA;
    AppUser judgeB;
    AppUser outsider;
    Track se;
    Candidate candidate;
    List<Criterion> sheet;
    long sessionId;
    long slotId;

    @BeforeEach
    void setUp() {
        organizer = createLinkedUser("user_s_org", "s.org@dxlab.vn", Role.ORGANIZER);
        judgeA = createLinkedUser("user_s_a", "s.a@dxlab.vn", Role.MEMBER);
        judgeB = createLinkedUser("user_s_b", "s.b@dxlab.vn", Role.MEMBER);
        outsider = createLinkedUser("user_s_out", "s.out@dxlab.vn", Role.MEMBER);
        se = tracks.findByCode("SE").orElseThrow();
        sheet = criteria.findApplicableToTrack(se.getId());

        Candidate c = new Candidate();
        c.setStudentCode("SE900");
        c.setFullName("Ứng viên chấm điểm");
        c.setTrack(se);
        c.setScreeningStatus(ScreeningStatus.PASSED);
        candidate = candidates.saveAndFlush(c);

        sessionId = id(send("POST", "/api/sessions", organizer, """
                {"trackId":%d,"sessionDate":"2026-11-25","startTime":"10:00","endTime":"11:00","rotatingMemberIds":[%d,%d]}
                """.formatted(se.getId(), judgeA.getId(), judgeB.getId())));
        slotId = id(send("POST", "/api/sessions/" + sessionId + "/slots", organizer,
                "{\"candidateId\":%d}".formatted(candidate.getId())));
    }

    @Test
    void onlyPanelCanScoreAndOnlyAfterAttendance() {
        assertThat(send("GET", sheetUri(), judgeA, null)).hasStatus(HttpStatus.FORBIDDEN);
        markAttended();

        assertThat(send("GET", sheetUri(), outsider, null)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(send("PUT", sheetUri(), outsider, body(full(8), "PASS", null, true))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(send("GET", sheetUri(), judgeA, null)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.items.length()").isEqualTo(sheet.size());
                    json.assertThat().extractingPath("$.items[0].group").isEqualTo("GENERAL");
                    json.assertThat().extractingPath("$.editable").isEqualTo(true);
                    json.assertThat().extractingPath("$.version").isNull();
                });
        assertThat(send("GET", "/api/me/scoring", judgeA, null)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$[*].studentCode").asArray().containsExactly("SE900");
                    json.assertThat().extractingPath("$[0].sheetStatus").isEqualTo("NONE");
                });
        assertThat(send("GET", "/api/me/scoring", outsider, null)).hasStatusOk().bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void draftCanBePartialButSubmissionMustBeComplete() {
        markAttended();
        String partial = "[{\"criterionId\":%d,\"value\":7}]".formatted(sheet.getFirst().getId());

        MvcTestResult draft = send("PUT", sheetUri(), judgeA, body(partial, null, null, false));
        assertThat(draft).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.submittedAt").isNull();
                    json.assertThat().extractingPath("$.items[0].value").isEqualTo(7);
                });
        int v1 = version(draft);

        assertThat(send("PUT", sheetUri(), judgeA, body(partial, "PASS", v1, true)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.code").isEqualTo("INCOMPLETE_SHEET");
        assertThat(send("PUT", sheetUri(), judgeA, body(full(8), null, v1, true)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.message").asString()
                .contains("Đề xuất");

        MvcTestResult submitted = send("PUT", sheetUri(), judgeA, body(full(8), "PASS", v1, true));
        assertThat(submitted).hasStatusOk().bodyJson().extractingPath("$.submittedAt").isNotNull();
        assertThat(version(submitted)).isGreaterThan(v1);

        // Đã nộp thì không được xóa bớt điểm
        String clear = "[{\"criterionId\":%d,\"value\":null}]".formatted(sheet.getFirst().getId());
        assertThat(send("PUT", sheetUri(), judgeA, body(clear, "PASS", version(submitted), false)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.code").isEqualTo("INCOMPLETE_SHEET");
    }

    @Test
    void secondSaveWithOldVersionIsRejected() {
        markAttended();
        int v = version(send("PUT", sheetUri(), judgeA, body(full(7), null, null, false)));

        assertThat(send("PUT", sheetUri(), judgeA, body(full(8), null, v, false))).hasStatusOk();
        assertThat(send("PUT", sheetUri(), judgeA, body(full(9), null, v, false)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("STALE_DATA");
        // Phiếu đã có mà gửi version null (mở phiếu trước khi tab khác tạo) cũng bị chặn
        assertThat(send("PUT", sheetUri(), judgeA, body(full(9), null, null, false)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void judgesSeeOthersOnlyAfterSubmittingAndDraftsStayHidden() {
        markAttended();
        send("PUT", sheetUri(), judgeA, body(full(8), "PASS", null, true));
        send("PUT", sheetUri(), judgeB, body(full(6), null, null, false));

        assertThat(send("GET", scoresUri(), judgeB, null))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("SUBMIT_FIRST");
        assertThat(send("GET", scoresUri(), judgeA, null)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.submittedCount").isEqualTo(1);
                    json.assertThat().extractingPath("$.evaluators[*].name").asArray().containsExactly("s.a@dxlab.vn");
                    json.assertThat().extractingPath("$.generalAverage").isEqualTo(8.0);
                    json.assertThat().extractingPath("$.professionalAverage").isEqualTo(8.0);
                });

        send("PUT", sheetUri(), judgeB, body(full(6), "FAIL", currentVersion(judgeB), true));
        assertThat(send("GET", scoresUri(), judgeB, null)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.submittedCount").isEqualTo(2);
                    json.assertThat().extractingPath("$.criteria[0].average").isEqualTo(7.0);
                    json.assertThat().extractingPath("$.criteria[0].count").isEqualTo(2);
                });
        // Người ngoài hội đồng không xem được
        assertThat(send("GET", scoresUri(), outsider, null)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void progressListsWhoHasNotSubmitted() {
        markAttended();
        send("PUT", sheetUri(), judgeA, body(full(8), "PASS", null, true));
        send("PUT", sheetUri(), judgeB, body(full(6), null, null, false));

        assertThat(send("GET", "/api/sessions/" + sessionId + "/progress", judgeA, null)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.candidates[0].submitted").isEqualTo(1);
                    json.assertThat().extractingPath("$.candidates[0].drafts").isEqualTo(1);
                    json.assertThat().extractingPath("$.candidates[0].pending").asArray()
                            .contains("s.b@dxlab.vn", "s.org@dxlab.vn").doesNotContain("s.a@dxlab.vn");
                });
        assertThat(send("GET", "/api/sessions/" + sessionId + "/progress", outsider, null)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsCriteriaOfAnotherTrack() {
        markAttended();
        Track ai = tracks.findByCode("AI").orElseThrow();
        Long aiCriterion = criteria.findByTrackIdAndActiveTrueOrderBySortOrderAsc(ai.getId()).getFirst().getId();

        assertThat(send("PUT", sheetUri(), judgeA, body("[{\"criterionId\":%d,\"value\":8}]".formatted(aiCriterion), null, null, false)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CRITERION");
    }

    @Test
    void approvedDecisionLocksSheetAndScoresLockAttendance() {
        markAttended();
        int v = version(send("PUT", sheetUri(), judgeA, body(full(8), "PASS", null, true)));

        assertThat(send("PUT", "/api/slots/" + slotId + "/attendance", organizer, "{\"attendance\":\"ABSENT\"}"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("ATTENDANCE_LOCKED");

        Decision d = new Decision();
        d.setCandidate(candidate);
        d.setApproved(true);
        d.setApprovedBy(organizer);
        d.setApprovedAt(Instant.now());
        decisions.saveAndFlush(d);

        assertThat(send("PUT", sheetUri(), judgeA, body(full(9), "PASS", v, true)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("SCORING_LOCKED");
        assertThat(send("GET", sheetUri(), judgeA, null)).hasStatusOk().bodyJson()
                .extractingPath("$.editable").isEqualTo(false);
    }

    // --- helpers ---

    private void markAttended() {
        assertThat(send("PUT", "/api/slots/" + slotId + "/attendance", organizer, "{\"attendance\":\"ATTENDED\"}"))
                .hasStatusOk();
    }

    private String sheetUri() {
        return "/api/candidates/" + candidate.getId() + "/scoresheet";
    }

    private String scoresUri() {
        return "/api/candidates/" + candidate.getId() + "/scores";
    }

    private String full(int value) {
        return sheet.stream().map(c -> "{\"criterionId\":%d,\"value\":%d}".formatted(c.getId(), value))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String body(String scores, String recommendation, Integer version, boolean submit) {
        return "{\"scores\":%s,\"comment\":\"Trả lời tốt\",\"recommendation\":%s,\"version\":%s,\"submit\":%s}".formatted(
                scores, recommendation == null ? "null" : "\"" + recommendation + "\"", version, submit);
    }

    private int currentVersion(AppUser judge) {
        return version(send("GET", sheetUri(), judge, null));
    }

    private MvcTestResult send(String method, String uri, AppUser as, String json) {
        var req = switch (method) {
            case "GET" -> mvc.get().uri(uri);
            case "POST" -> mvc.post().uri(uri);
            case "PUT" -> mvc.put().uri(uri);
            default -> throw new IllegalArgumentException(method);
        };
        req = req.header(HttpHeaders.AUTHORIZATION, bearer(as));
        if (json != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return req.exchange();
    }

    private static long id(MvcTestResult result) {
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private static int version(MvcTestResult result) {
        return ((Number) JsonPath.read(body(result), "$.version")).intValue();
    }

    private static String body(MvcTestResult result) {
        try {
            String s = result.getResponse().getContentAsString();
            assertThat(result.getResponse().getStatus()).as(s).isLessThan(300);
            return s;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
