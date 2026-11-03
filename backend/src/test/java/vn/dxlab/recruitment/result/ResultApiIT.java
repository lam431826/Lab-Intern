package vn.dxlab.recruitment.result;

import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import vn.dxlab.recruitment.interview.Attendance;
import vn.dxlab.recruitment.interview.InterviewSession;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.interview.InterviewSlot;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.interview.PanelMember;
import vn.dxlab.recruitment.interview.PanelRole;
import vn.dxlab.recruitment.scoring.EvaluatorNote;
import vn.dxlab.recruitment.scoring.EvaluatorNoteRepository;
import vn.dxlab.recruitment.scoring.Recommendation;
import vn.dxlab.recruitment.scoring.Score;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.track.CriterionGroup;
import vn.dxlab.recruitment.track.CriterionRepository;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bộ dữ liệu mẫu ngạch AI, chỉ tiêu 2, hội đồng 2 người (Giảng viên, Người tổ chức), trọng số 40/60, sàn 7,0.
 * <pre>
 *            giám khảo 1        giám khảo 2       chung  chuyên môn  tổng
 * AI01       chung 9, CM 9      chung 9, CM 9     9,00   9,00        9,00   hạng 1 → Trúng tuyển
 * AI02       chung 8, CM 7      chung 8, CM 7     8,00   7,00        7,40   hạng 3 → Dự bị
 * AI03       chung 7, CM 8      chung 8, CM 9     7,50   8,50        8,10   hạng 2 → Trúng tuyển
 * AI04       chung 6, CM 6      chung 6, CM 6     6,00   6,00        6,00   hạng 4 → Loại (dưới sàn)
 * AI05       vắng mặt                                                         → Loại
 * </pre>
 */
@Transactional
class ResultApiIT extends ApiIntegrationTest {

    @Autowired TrackRepository tracks;
    @Autowired CandidateRepository candidates;
    @Autowired CriterionRepository criteria;
    @Autowired InterviewSessionRepository sessions;
    @Autowired InterviewSlotRepository slots;
    @Autowired EvaluatorNoteRepository notes;
    @Autowired ScoreRepository scores;

    AppUser lecturer;
    AppUser organizer;
    Track ai;
    InterviewSession session;
    Candidate ai01, ai02, ai03, ai04, ai05;
    int slotMinute;

    @BeforeEach
    void setUp() {
        lecturer = createLinkedUser("user_r_lec", "r.lec@dxlab.vn", Role.LECTURER);
        organizer = createLinkedUser("user_r_org", "r.org@dxlab.vn", Role.ORGANIZER);
        ai = tracks.findByCode("AI").orElseThrow();
        ai.setQuota(2);
        tracks.saveAndFlush(ai);

        session = new InterviewSession();
        session.setTrack(ai);
        session.setSessionDate(LocalDate.of(2026, 11, 28));
        session.setStartTime(LocalTime.of(10, 0));
        session.setEndTime(LocalTime.of(12, 0));
        panel(lecturer, PanelRole.LECTURER);
        panel(organizer, PanelRole.ORGANIZER);
        sessions.saveAndFlush(session);

        ai01 = interviewed("AI01", 9, 9, 9, 9);
        ai02 = interviewed("AI02", 8, 7, 8, 7);
        ai03 = interviewed("AI03", 7, 8, 8, 9);
        ai04 = interviewed("AI04", 6, 6, 6, 6);
        ai05 = candidate("AI05");
        slot(ai05, Attendance.ABSENT);
    }

    @Test
    void tableMatchesHandCalculation() {
        assertThat(get("/api/results/" + ai.getId(), lecturer)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.rows[*].studentCode").asArray()
                            .containsExactly("AI01", "AI03", "AI02", "AI04", "AI05");
                    json.assertThat().extractingPath("$.rows[*].total").asArray()
                            .containsExactly(9.0, 8.1, 7.4, 6.0, null);
                    json.assertThat().extractingPath("$.rows[1].generalAverage").isEqualTo(7.5);
                    json.assertThat().extractingPath("$.rows[1].professionalAverage").isEqualTo(8.5);
                    json.assertThat().extractingPath("$.rows[*].rank").asArray().containsExactly(1, 2, 3, 4, null);
                    json.assertThat().extractingPath("$.rows[*].suggestion").asArray()
                            .containsExactly("HIRED", "HIRED", "WAITLIST", "REJECTED", "REJECTED");
                    json.assertThat().extractingPath("$.rows[0].passVotes").isEqualTo(2);
                    json.assertThat().extractingPath("$.seatsLeft").isEqualTo(2);
                });
        // Đổi trọng số thì bảng tính lại ngay: 50/50 → AI02 = 7,50; AI03 = 8,00
        assertThat(put("/api/settings", lecturer,
                "{\"weightGeneral\":0.5,\"weightProfessional\":0.5,\"passThreshold\":7,\"slaHours\":36,\"slotMinutes\":15}"))
                .hasStatusOk();
        assertThat(get("/api/results/" + ai.getId(), lecturer)).hasStatusOk().bodyJson()
                .extractingPath("$.rows[*].total").asArray().containsExactly(9.0, 8.0, 7.5, 6.0, null);
    }

    /** Luồng đầy đủ: chốt, đủ chỉ tiêu, phê duyệt, khóa, offer bị từ chối, gọi dự bị theo thứ hạng. */
    @Test
    void decisionApprovalOfferAndWaitlistFlow() {
        assertThat(decide(ai01, "HIRED", null, null, organizer)).hasStatusOk();
        assertThat(decide(ai03, "HIRED", null, null, organizer)).hasStatusOk();
        assertCode(decide(ai02, "HIRED", "Muốn nhận thêm", null, organizer), HttpStatus.CONFLICT, "QUOTA_FULL");
        assertThat(decide(ai02, "WAITLIST", null, null, organizer)).hasStatusOk();
        assertCode(decide(ai04, "WAITLIST", null, null, organizer), HttpStatus.UNPROCESSABLE_CONTENT, "DECISION_NOTE_REQUIRED");
        assertThat(decide(ai04, "REJECTED", null, null, organizer)).hasStatusOk();
        assertThat(decide(ai05, "REJECTED", null, null, organizer)).hasStatusOk();

        // Chỉ Giảng viên phê duyệt
        assertThat(post("/api/results/" + ai.getId() + "/approve", organizer, "{}")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/api/results/" + ai.getId() + "/approve", lecturer, "{}")).hasStatusOk().bodyJson()
                .extractingPath("$.rows[*].decision.approved").asArray().containsOnly(true);
        assertCode(decide(ai01, "WAITLIST", "Đổi ý", version(ai01), lecturer), HttpStatus.CONFLICT, "DECISION_LOCKED");

        // Offer: phải gửi email trước khi ghi nhận phản hồi
        assertCode(offer(ai01, "NOT_SENT", "ACCEPTED"), HttpStatus.UNPROCESSABLE_CONTENT, "EMAIL_NOT_SENT");
        assertThat(offer(ai01, "SENT", "DECLINED")).hasStatusOk();
        assertThat(get("/api/results/" + ai.getId(), lecturer)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.seatsLeft").isEqualTo(1);
                    json.assertThat().extractingPath("$.nextWaitlistCandidateId").isEqualTo(ai02.getId().intValue());
                });
        assertCode(post("/api/candidates/" + ai01.getId() + "/decision/reopen", lecturer,
                "{\"version\":%d}".formatted(version(ai01))), HttpStatus.CONFLICT, "DECISION_ALREADY_SENT");

        assertThat(post("/api/candidates/" + ai02.getId() + "/decision/promote", organizer,
                "{\"version\":%d}".formatted(version(ai02))))
                .hasStatusOk().bodyJson().extractingPath("$.finalStatus").isEqualTo("HIRED");
        assertThat(get("/api/results/" + ai.getId(), lecturer)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.seatsLeft").isEqualTo(0);
                    json.assertThat().extractingPath("$.nextWaitlistCandidateId").isNull();
                });
    }

    @Test
    void decisionNeedsCompleteScoresAndValidTransfer() {
        Candidate partial = candidate("AI06");
        slot(partial, Attendance.ATTENDED);
        sheet(partial, lecturer, 8, 8);
        assertCode(decide(partial, "HIRED", null, null, organizer), HttpStatus.UNPROCESSABLE_CONTENT, "SCORES_INCOMPLETE");

        assertCode(decide(ai02, "TRANSFER", "Hợp ngạch khác", null, organizer), HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_TRANSFER");
        Track se = tracks.findByCode("SE").orElseThrow();
        assertThat(decide(ai02, "TRANSFER", "Mạnh về lập trình, SE còn chỉ tiêu", se.getId(), organizer))
                .hasStatusOk().bodyJson().extractingPath("$.transferTrackCode").isEqualTo("SE");
        // Sửa lại khi chưa phê duyệt mà gửi version cũ
        assertCode(decide(ai02, "WAITLIST", null, 999, organizer), HttpStatus.CONFLICT, "STALE_DATA");
    }

    @Test
    void visibilityExportAndDashboard() throws IOException {
        AppUser otherLead = createLinkedUser("user_r_lead", "r.lead@dxlab.vn", Role.LEAD);
        Track se = tracks.findByCode("SE").orElseThrow();
        se.setLead(otherLead);
        AppUser member = createLinkedUser("user_r_mem", "r.mem@dxlab.vn", Role.MEMBER);

        assertThat(get("/api/results/" + ai.getId(), otherLead)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/api/results", otherLead)).hasStatusOk().bodyJson()
                .extractingPath("$[*].trackCode").asArray().containsExactly("SE");
        assertThat(get("/api/results", member)).hasStatus(HttpStatus.FORBIDDEN);

        decide(ai01, "HIRED", null, null, organizer);
        MvcTestResult file = get("/api/results/export?trackId=" + ai.getId(), lecturer);
        assertThat(file).hasStatusOk();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file.getResponse().getContentAsByteArray()))) {
            Row first = wb.getSheet("Kết quả").getRow(1);
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("AI01");
            assertThat(first.getCell(8).getNumericCellValue()).isEqualTo(9.0);
            assertThat(first.getCell(12).getStringCellValue()).isEqualTo("Trúng tuyển");
            assertThat(wb.getSheet("Kết quả").getRow(3).getCell(8).getNumericCellValue()).isEqualTo(7.4);
        }

        assertThat(get("/api/dashboard", lecturer)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.tracks[?(@.trackCode=='AI')].attended").asArray().containsExactly(4);
                    json.assertThat().extractingPath("$.tracks[?(@.trackCode=='AI')].absent").asArray().containsExactly(1);
                    json.assertThat().extractingPath("$.tracks[?(@.trackCode=='AI')].scoresComplete").asArray().containsExactly(4);
                    json.assertThat().extractingPath("$.tracks[?(@.trackCode=='AI')].seatsTaken").asArray().containsExactly(1);
                    json.assertThat().extractingPath("$.myTasks.toApprove").isEqualTo(1);
                });
        assertThat(get("/api/dashboard", member)).hasStatusOk().bodyJson()
                .extractingPath("$.tracks").asArray().isEmpty();
    }

    // --- dữ liệu mẫu ---

    private void panel(AppUser user, PanelRole role) {
        PanelMember p = new PanelMember();
        p.setSession(session);
        p.setUser(user);
        p.setPanelRole(role);
        session.getPanel().add(p);
    }

    private Candidate interviewed(String code, int g1, int p1, int g2, int p2) {
        Candidate c = candidate(code);
        slot(c, Attendance.ATTENDED);
        sheet(c, lecturer, g1, p1);
        sheet(c, organizer, g2, p2);
        return c;
    }

    private Candidate candidate(String code) {
        Candidate c = new Candidate();
        c.setStudentCode(code);
        c.setFullName("Ứng viên " + code);
        c.setTrack(ai);
        c.setScreeningStatus(ScreeningStatus.PASSED);
        return candidates.saveAndFlush(c);
    }

    private void slot(Candidate c, Attendance attendance) {
        InterviewSlot s = new InterviewSlot();
        s.setSession(session);
        s.setCandidate(c);
        s.setStartTime(LocalTime.of(10, 0).plusMinutes(slotMinute));
        s.setEndTime(LocalTime.of(10, 15).plusMinutes(slotMinute));
        slotMinute += 15;
        s.setAttendance(attendance);
        slots.saveAndFlush(s);
    }

    /** Phiếu đã nộp: mọi tiêu chí chung cùng điểm {@code general}, mọi tiêu chí chuyên môn cùng điểm {@code professional}. */
    private void sheet(Candidate c, AppUser judge, int general, int professional) {
        List<Criterion> list = criteria.findApplicableToTrack(ai.getId());
        for (Criterion cr : list) {
            Score s = new Score();
            s.setCandidate(c);
            s.setEvaluator(judge);
            s.setCriterion(cr);
            s.setValue(cr.getGroup() == CriterionGroup.GENERAL ? general : professional);
            scores.save(s);
        }
        EvaluatorNote n = new EvaluatorNote();
        n.setCandidate(c);
        n.setEvaluator(judge);
        n.setRecommendation(general + professional >= 14 ? Recommendation.PASS : Recommendation.FAIL);
        n.setSubmittedAt(Instant.now());
        notes.saveAndFlush(n);
    }

    // --- HTTP ---

    private MvcTestResult decide(Candidate c, String status, String note, Object transferOrVersion, AppUser as) {
        Long transfer = null;
        Integer version = null;
        if ("TRANSFER".equals(status) && transferOrVersion instanceof Long t) {
            transfer = t;
        } else if (transferOrVersion instanceof Integer v) {
            version = v;
        }
        if (version == null) {
            version = currentVersion(c);
        }
        return put("/api/candidates/" + c.getId() + "/decision", as,
                "{\"finalStatus\":\"%s\",\"transferTrackId\":%s,\"note\":%s,\"version\":%s}".formatted(status, transfer,
                        note == null ? "null" : "\"" + note + "\"", version));
    }

    private MvcTestResult offer(Candidate c, String email, String offer) {
        return put("/api/candidates/" + c.getId() + "/decision/offer", organizer,
                "{\"emailStatus\":\"%s\",\"offerStatus\":\"%s\",\"version\":%d}".formatted(email, offer, version(c)));
    }

    /** Version kết luận hiện tại; {@code null} nếu chưa có kết luận. */
    private Integer currentVersion(Candidate c) {
        String body = body(get("/api/results/" + ai.getId(), lecturer));
        List<Integer> v = JsonPath.read(body, "$.rows[?(@.candidateId==" + c.getId() + ")].decision.version");
        return v.isEmpty() ? null : v.getFirst();
    }

    private int version(Candidate c) {
        return currentVersion(c);
    }

    private MvcTestResult get(String uri, AppUser as) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(as)).exchange();
    }

    private MvcTestResult put(String uri, AppUser as, String json) {
        return mvc.put().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(as))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult post(String uri, AppUser as, String json) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(as))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
