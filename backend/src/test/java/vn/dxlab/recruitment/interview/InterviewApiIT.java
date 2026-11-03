package vn.dxlab.recruitment.interview;

import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Sheet;
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
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class InterviewApiIT extends ApiIntegrationTest {

    @Autowired TrackRepository tracks;
    @Autowired CandidateRepository candidates;
    @Autowired InterviewSlotRepository slots;

    AppUser organizer;
    AppUser lecturer;
    AppUser seLead;
    AppUser member;
    AppUser outsider;
    Track se;

    @BeforeEach
    void setUp() {
        organizer = createLinkedUser("user_i_org", "i.org@dxlab.vn", Role.ORGANIZER);
        lecturer = createLinkedUser("user_i_lec", "i.lec@dxlab.vn", Role.LECTURER);
        seLead = createLinkedUser("user_i_lead", "i.lead@dxlab.vn", Role.LEAD);
        member = createLinkedUser("user_i_mem", "i.mem@dxlab.vn", Role.MEMBER);
        outsider = createLinkedUser("user_i_out", "i.out@dxlab.vn", Role.MEMBER);
        se = tracks.findByCode("SE").orElseThrow();
        se.setLead(seLead);
        tracks.saveAndFlush(se);
    }

    @Test
    void createsSessionWithFixedPanelAndWarnings() {
        assertThat(post("/api/sessions", organizer, session("2026-11-20", "09:00", "10:30", member.getId())))
                .hasStatus(HttpStatus.CREATED).bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.panel[*].panelRole").asArray()
                            .containsExactly("LECTURER", "ORGANIZER", "LEAD", "ROTATING");
                    json.assertThat().extractingPath("$.warnings[*].code").asArray()
                            .containsExactlyInAnyOrder("OUTSIDE_HOURS", "PANEL_SIZE");
                });
    }

    @Test
    void autoScheduleFillsSessionInScreeningOrder() {
        Candidate first = passed("SE101", Instant.parse("2026-11-01T01:00:00Z"));
        Candidate second = passed("SE102", Instant.parse("2026-11-01T02:00:00Z"));
        passed("SE103", Instant.parse("2026-11-01T03:00:00Z"));
        candidate("SE104", ScreeningStatus.PENDING, null);
        long sessionId = createSession("2026-11-20", "10:00", "10:30");

        assertThat(post("/api/sessions/" + sessionId + "/auto-schedule", organizer, "{}"))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.scheduled[*].studentCode").asArray().containsExactly("SE101", "SE102");
                    json.assertThat().extractingPath("$.scheduled[*].timeLabel").asArray()
                            .containsExactly("10h - 10h15", "10h15 - 10h30");
                    json.assertThat().extractingPath("$.notScheduled").asArray().containsExactly("SE103");
                });
        assertThat(slots.findByCandidateIdAndActiveTrue(first.getId())).isPresent();
        assertThat(slots.findByCandidateIdAndActiveTrue(second.getId())).isPresent();
    }

    @Test
    void manualSchedulingRejectsOverlapAndUnpassedOrDuplicateCandidates() {
        Candidate a = passed("SE201", null);
        Candidate b = passed("SE202", null);
        Candidate pending = candidate("SE203", ScreeningStatus.PENDING, null);
        long sessionId = createSession("2026-11-20", "10:00", "11:00");

        assertThat(post("/api/sessions/" + sessionId + "/slots", organizer, slot(a.getId(), "10:00")))
                .hasStatus(HttpStatus.CREATED);
        assertCode(post("/api/sessions/" + sessionId + "/slots", organizer, slot(b.getId(), "10:10")),
                HttpStatus.CONFLICT, "SLOT_OVERLAP");
        assertCode(post("/api/sessions/" + sessionId + "/slots", organizer, slot(b.getId(), "10:50")),
                HttpStatus.UNPROCESSABLE_CONTENT, "SLOT_OUTSIDE_SESSION");
        assertCode(post("/api/sessions/" + sessionId + "/slots", organizer, slot(pending.getId(), null)),
                HttpStatus.UNPROCESSABLE_CONTENT, "CANDIDATE_NOT_PASSED");
        assertCode(post("/api/sessions/" + sessionId + "/slots", organizer, slot(a.getId(), null)),
                HttpStatus.CONFLICT, "CANDIDATE_ALREADY_SCHEDULED");
        // Không giờ chỉ định: lấy chỗ trống sớm nhất sau slot đã có
        assertThat(post("/api/sessions/" + sessionId + "/slots", organizer, slot(b.getId(), null)))
                .hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.timeLabel").isEqualTo("10h15 - 10h30");
    }

    /** Kịch bản nghiệm thu B6: vắng PV1, dời sang PV2, không dời lần hai, hủy PV2 thì PV1 hiệu lực lại. */
    @Test
    void absentCandidateIsRescheduledOnceAndHistoryIsKept() {
        Candidate c = passed("SE301", null);
        long pv1Session = createSession("2026-11-20", "10:00", "11:00");
        long pv2Session = createSession("2026-11-21", "16:00", "17:00");
        long pv1 = id(post("/api/sessions/" + pv1Session + "/slots", organizer, slot(c.getId(), "10:30")));

        // Thành viên luân chuyển của ca điểm danh vắng
        assertThat(put("/api/slots/" + pv1 + "/attendance", member, "{\"attendance\":\"ABSENT\",\"note\":\"Ốm\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.attendance").isEqualTo("ABSENT");

        MvcTestResult moved = post("/api/slots/" + pv1 + "/reschedule", organizer,
                "{\"sessionId\":%d,\"startTime\":\"16:15\",\"reason\":\"Vắng PV1 do ốm\"}".formatted(pv2Session));
        assertThat(moved).hasStatus(HttpStatus.CREATED).bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.slotType").isEqualTo("PV2");
                    json.assertThat().extractingPath("$.attendance").isEqualTo("PENDING");
                    json.assertThat().extractingPath("$.timeLabel").isEqualTo("16h15 - 16h30");
                });
        long pv2 = id(moved);

        assertThat(get("/api/candidates/" + c.getId() + "/slots", organizer)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$[*].active").asArray().containsExactly(false, true);
                    json.assertThat().extractingPath("$[0].replacedById").isEqualTo((int) pv2);
                    json.assertThat().extractingPath("$[0].note").isEqualTo("Vắng PV1 do ốm");
                });
        assertCode(post("/api/slots/" + pv1 + "/reschedule", organizer,
                "{\"sessionId\":%d,\"reason\":\"x\"}".formatted(pv2Session)), HttpStatus.CONFLICT, "SLOT_INACTIVE");
        assertCode(post("/api/slots/" + pv2 + "/reschedule", organizer,
                "{\"sessionId\":%d,\"reason\":\"Lại xin dời\"}".formatted(pv1Session)),
                HttpStatus.UNPROCESSABLE_CONTENT, "RESCHEDULE_LIMIT");

        assertThat(mvc.delete().uri("/api/slots/" + pv2).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.NO_CONTENT);
        InterviewSlot restored = slots.findByCandidateIdAndActiveTrue(c.getId()).orElseThrow();
        assertThat(restored.getId()).isEqualTo(pv1);
        assertThat(restored.getReplacedBy()).isNull();
    }

    @Test
    void attendedCandidateCannotBeRescheduled() {
        Candidate c = passed("SE401", null);
        long sessionId = createSession("2026-11-20", "10:00", "11:00");
        long slotId = id(post("/api/sessions/" + sessionId + "/slots", organizer, slot(c.getId(), null)));

        assertThat(put("/api/slots/" + slotId + "/attendance", lecturer, "{\"attendance\":\"ATTENDED\"}")).hasStatusOk();
        assertCode(post("/api/slots/" + slotId + "/reschedule", organizer,
                "{\"sessionId\":%d,\"reason\":\"x\"}".formatted(sessionId)), HttpStatus.CONFLICT, "SLOT_ATTENDED");
    }

    @Test
    void onlyPanelSeesAndRunsSession() {
        Candidate c = passed("SE501", null);
        long sessionId = createSession("2026-11-20", "10:00", "11:00");
        long slotId = id(post("/api/sessions/" + sessionId + "/slots", organizer, slot(c.getId(), null)));

        assertThat(get("/api/sessions", member)).hasStatusOk().bodyJson()
                .extractingPath("$[*].id").asArray().containsExactly((int) sessionId);
        assertThat(get("/api/sessions", outsider)).hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(get("/api/sessions/" + sessionId, outsider)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(put("/api/slots/" + slotId + "/attendance", outsider, "{\"attendance\":\"ATTENDED\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        // Thành viên hội đồng xem được hồ sơ ứng viên của ca
        assertThat(get("/api/candidates/" + c.getId(), member)).hasStatusOk();
        assertThat(put("/api/sessions/" + sessionId + "/log", member, "{\"logSummary\":\"Ca đúng giờ\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.logSummary").isEqualTo("Ca đúng giờ");
        assertThat(put("/api/sessions/" + sessionId + "/log", outsider, "{\"logSummary\":\"x\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void sessionWithSlotsCannotShrinkOrBeDeleted() {
        Candidate c = passed("SE601", null);
        long sessionId = createSession("2026-11-20", "10:00", "11:00");
        post("/api/sessions/" + sessionId + "/slots", organizer, slot(c.getId(), "10:30"));

        assertCode(put("/api/sessions/" + sessionId, organizer, session("2026-11-20", "10:00", "10:30", member.getId())),
                HttpStatus.UNPROCESSABLE_CONTENT, "SLOT_OUTSIDE_SESSION");
        assertCode(mvc.delete().uri("/api/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer(organizer)).exchange(),
                HttpStatus.CONFLICT, "SESSION_IN_USE");
        // Lịch đã xếp thì không loại hồ sơ được (quy tắc B5 vẫn giữ)
        assertThat(slots.existsByCandidateIdAndActiveTrue(c.getId())).isTrue();
    }

    @Test
    void panelCanBeChangedAndScheduleExported() throws IOException {
        Candidate c = passed("SE701", null);
        c.setPhone("0901234567");
        candidates.saveAndFlush(c);
        long sessionId = createSession("2026-11-20", "16:00", "17:00");
        post("/api/sessions/" + sessionId + "/slots", organizer, slot(c.getId(), null));

        assertThat(put("/api/sessions/" + sessionId + "/panel", organizer,
                "{\"rotatingMemberIds\":[%d,%d]}".formatted(outsider.getId(), lecturer.getId())))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    // Giảng viên đã là thành viên cố định nên không bị thêm lần hai
                    json.assertThat().extractingPath("$.panel.length()").isEqualTo(4);
                    json.assertThat().extractingPath("$.panel[*].userId").asArray()
                            .contains(outsider.getId().intValue()).doesNotContain(member.getId().intValue());
                });

        MvcTestResult file = get("/api/sessions/export?trackId=" + se.getId(), lecturer);
        assertThat(file).hasStatusOk();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("Lịch phỏng vấn");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("20/11/2026");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("16h - 16h15");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("SE701");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("0901234567");
        }
        assertThat(get("/api/sessions/export", member)).hasStatus(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private long createSession(String date, String start, String end) {
        return id(post("/api/sessions", organizer, session(date, start, end, member.getId())));
    }

    private String session(String date, String start, String end, Long rotatingId) {
        return """
                {"trackId":%d,"sessionDate":"%s","startTime":"%s","endTime":"%s","room":"P.101","rotatingMemberIds":[%d]}
                """.formatted(se.getId(), date, start, end, rotatingId);
    }

    private static String slot(Long candidateId, String start) {
        return "{\"candidateId\":%d,\"startTime\":%s}".formatted(candidateId, start == null ? "null" : "\"" + start + "\"");
    }

    private Candidate passed(String code, Instant screenedAt) {
        return candidate(code, ScreeningStatus.PASSED, screenedAt);
    }

    private Candidate candidate(String code, ScreeningStatus status, Instant screenedAt) {
        Candidate c = new Candidate();
        c.setStudentCode(code);
        c.setFullName("Ứng viên " + code);
        c.setTrack(se);
        c.setScreeningStatus(status);
        c.setScreenedAt(screenedAt);
        return candidates.saveAndFlush(c);
    }

    private MvcTestResult post(String uri, AppUser as, String body) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(as))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult put(String uri, AppUser as, String body) {
        return mvc.put().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(as))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult get(String uri, AppUser as) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(as)).exchange();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static long id(MvcTestResult result) {
        assertThat(result.getResponse().getStatus()).as(() -> body(result)).isLessThan(300);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
