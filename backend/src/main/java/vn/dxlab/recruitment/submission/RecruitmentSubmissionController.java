package vn.dxlab.recruitment.submission;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.dxlab.recruitment.submission.dto.CreateSubmissionRequest;
import vn.dxlab.recruitment.submission.dto.MarkSentRequest;
import vn.dxlab.recruitment.submission.dto.SubmissionDto;
import vn.dxlab.recruitment.submission.dto.SubmissionItemDto;
import vn.dxlab.recruitment.submission.dto.SubmissionItemRequest;
import vn.dxlab.recruitment.submission.dto.UpdateSubmissionRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class RecruitmentSubmissionController {

    private static final String CAN_VIEW = "hasAnyRole('ORGANIZER', 'LECTURER', 'LEAD')";
    private final RecruitmentSubmissionService service;

    public RecruitmentSubmissionController(RecruitmentSubmissionService service) {
        this.service = service;
    }

    @GetMapping("/api/recruitment-submissions")
    @PreAuthorize(CAN_VIEW)
    public List<SubmissionDto> list(@RequestParam(required = false) String termCode,
                                    @RequestParam(required = false) Long trackId,
                                    @RequestParam(required = false) SubmissionStatus status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                    @RequestParam(required = false) String q) {
        return service.list(termCode, trackId, status, from, to, q);
    }

    @GetMapping("/api/recruitment-submissions/{id}")
    @PreAuthorize(CAN_VIEW)
    public SubmissionDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/recruitment-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public SubmissionDto create(@Valid @RequestBody CreateSubmissionRequest req) {
        return service.create(req);
    }

    @PutMapping("/api/recruitment-submissions/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public SubmissionDto update(@PathVariable Long id, @Valid @RequestBody UpdateSubmissionRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/recruitment-submissions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/api/recruitment-submissions/{id}/mark-sent")
    @PreAuthorize("hasRole('ORGANIZER')")
    public SubmissionDto markSent(@PathVariable Long id, @Valid @RequestBody MarkSentRequest req) {
        return service.markSent(id, req);
    }

    @PostMapping("/api/recruitment-submissions/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public SubmissionItemDto addItem(@PathVariable Long id, @Valid @RequestBody SubmissionItemRequest req) {
        return service.addItem(id, req);
    }

    @PutMapping("/api/recruitment-submission-items/{itemId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public SubmissionItemDto updateItem(@PathVariable Long itemId, @Valid @RequestBody SubmissionItemRequest req) {
        return service.updateItem(itemId, req);
    }

    @DeleteMapping("/api/recruitment-submission-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public void deleteItem(@PathVariable Long itemId) {
        service.deleteItem(itemId);
    }

    @PostMapping(value = "/api/recruitment-submission-items/{itemId}/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public SubmissionItemDto uploadFile(@PathVariable Long itemId,
                                        @RequestPart("file") MultipartFile file,
                                        @RequestParam Integer version) {
        return service.uploadFile(itemId, file, version);
    }

    @GetMapping("/api/recruitment-submission-items/{itemId}/file")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long itemId) {
        RecruitmentSubmissionService.FileDownload file = service.downloadFile(itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.size())
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).noStore().mustRevalidate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-File-SHA256", file.sha256())
                .body(file.data());
    }
}
