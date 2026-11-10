package vn.dxlab.recruitment.submission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RecruitmentSubmissionApiIT extends ApiIntegrationTest {

    @Autowired RecruitmentSubmissionRepository submissions;
    @Autowired RecruitmentSubmissionItemRepository items;
    @Autowired TrackRepository tracks;

    AppUser organizer;
    Track se;

    @BeforeEach
    void setUp() {
        organizer = createLinkedUser("submission_org", "submission.org@dxlab.vn", Role.ORGANIZER);
        se = tracks.findByCode("SE").orElseThrow();
    }

    @Test
    void organizerCreatesSendsAndCannotMutateSnapshot() {
        Long submissionId = createDraft();
        Long itemId = addItem(submissionId, organizer, se.getId(), "Java Backend Intern", "JD Java");

        RecruitmentSubmission before = submissions.findById(submissionId).orElseThrow();
        assertThat(mvc.post().uri("/api/recruitment-submissions/" + submissionId + "/mark-sent")
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sentAt":"%s","recipient":"Phòng QHDN","externalReference":"Email #123","version":%d}
                        """.formatted(Instant.now().minusSeconds(60), before.getVersion())))
                .hasStatusOk().bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.status").isEqualTo("SENT");
                    json.assertThat().extractingPath("$.totalQuota").isEqualTo(5);
                });

        RecruitmentSubmissionItem item = items.findById(itemId).orElseThrow();
        assertThat(mvc.put().uri("/api/recruitment-submission-items/" + itemId)
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson(se.getId(), "Đổi sau khi gửi", "JD mới", item.getVersion())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("SUBMISSION_LOCKED");
        assertThat(mvc.delete().uri("/api/recruitment-submissions/" + submissionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void accessAndTrackScopedEditingAreEnforced() {
        Long submissionId = createDraft();
        AppUser member = createLinkedUser("submission_member", "submission.member@dxlab.vn", Role.MEMBER);
        AppUser lead = createLinkedUser("submission_lead", "submission.lead@dxlab.vn", Role.LEAD);
        se.setLead(lead);
        Track ai = tracks.findByCode("AI").orElseThrow();

        assertThat(mvc.get().uri("/api/recruitment-submissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/recruitment-submissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(lead))).hasStatusOk();

        assertThat(mvc.post().uri("/api/recruitment-submissions/" + submissionId + "/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(lead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson(se.getId(), "SE Intern", "JD", null)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/recruitment-submissions/" + submissionId + "/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(lead))
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson(ai.getId(), "AI Intern", "JD", null)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void validatesFileContentAndDownloadsAsSafeAttachment() {
        Long submissionId = createDraft();
        Long itemId = addItem(submissionId, organizer, se.getId(), "Backend Intern", null);
        RecruitmentSubmissionItem item = items.findById(itemId).orElseThrow();

        assertThat(mvc.post().uri("/api/recruitment-submission-items/" + itemId + "/file?version=" + item.getVersion())
                .multipart().file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not pdf".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_JD_FILE");

        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes();
        assertThat(mvc.post().uri("/api/recruitment-submission-items/" + itemId + "/file?version=" + item.getVersion())
                .multipart().file(new MockMultipartFile("file", "../JD Backend.pdf", "text/plain", pdf))
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .hasStatusOk().bodyJson().extractingPath("$.fileName").isEqualTo("JD Backend.pdf");

        var result = mvc.get().uri("/api/recruitment-submission-items/" + itemId + "/file")
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer)).exchange();
        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-File-SHA256")).hasSize(64);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(pdf);
    }

    @Test
    void cannotSendEmptySubmissionOrItemWithoutJd() {
        Long submissionId = createDraft();
        RecruitmentSubmission s = submissions.findById(submissionId).orElseThrow();
        assertThat(markSent(submissionId, s.getVersion())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("SUBMISSION_EMPTY");

        addItem(submissionId, organizer, se.getId(), "Backend Intern", null);
        assertThat(markSent(submissionId, s.getVersion())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("JD_REQUIRED");
    }

    private Long createDraft() {
        assertThat(mvc.post().uri("/api/recruitment-submissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"termCode":"FA26","title":"Tuyển thực tập Fall 2026","recipient":"Phòng QHDN"}
                        """))
                .hasStatus(HttpStatus.CREATED);
        return submissions.findAll().stream().max(java.util.Comparator.comparing(RecruitmentSubmission::getId))
                .orElseThrow().getId();
    }

    private Long addItem(Long submissionId, AppUser actor, Long trackId, String title, String jd) {
        assertThat(mvc.post().uri("/api/recruitment-submissions/" + submissionId + "/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson(trackId, title, jd, null)))
                .hasStatus(HttpStatus.CREATED);
        return items.findAll().stream().filter(i -> i.getSubmission().getId().equals(submissionId))
                .max(java.util.Comparator.comparing(RecruitmentSubmissionItem::getId)).orElseThrow().getId();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult markSent(Long id, int version) {
        return mvc.post().uri("/api/recruitment-submissions/" + id + "/mark-sent")
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sentAt":"%s","recipient":"Phòng QHDN","version":%d}
                        """.formatted(Instant.now().minusSeconds(60), version)).exchange();
    }

    private static String itemJson(Long trackId, String title, String jd, Integer version) {
        return """
                {"trackId":%d,"positionTitle":"%s","quota":5,"jdContent":%s,"sortOrder":1,"version":%s}
                """.formatted(trackId, title, jd == null ? "null" : "\"" + jd + "\"",
                version == null ? "null" : version);
    }
}
