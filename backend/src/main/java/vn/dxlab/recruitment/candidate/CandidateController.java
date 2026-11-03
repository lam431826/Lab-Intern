package vn.dxlab.recruitment.candidate;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.dxlab.recruitment.audit.HistoryEntryDto;
import vn.dxlab.recruitment.candidate.dto.CandidateDto;
import vn.dxlab.recruitment.candidate.dto.CandidateRequest;
import vn.dxlab.recruitment.candidate.dto.ImportReport;
import vn.dxlab.recruitment.candidate.dto.ScreeningRequest;
import vn.dxlab.recruitment.candidate.dto.TrackScreeningSummary;
import vn.dxlab.recruitment.common.error.BusinessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateService candidates;
    private final ScreeningService screening;
    private final CandidateImportService importer;

    public CandidateController(CandidateService candidates, ScreeningService screening, CandidateImportService importer) {
        this.candidates = candidates;
        this.screening = screening;
        this.importer = importer;
    }

    /** Lead chỉ nhận về hồ sơ ngạch mình phụ trách; truyền trackId của ngạch khác thì 403. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER', 'LEAD')")
    public List<CandidateDto> list(@RequestParam(required = false) Long trackId,
                                   @RequestParam(required = false) ScreeningStatus status,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "false") boolean overdue) {
        return candidates.list(new CandidateService.Filter(trackId, status, q, overdue));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER', 'LEAD')")
    public List<TrackScreeningSummary> summary() {
        return candidates.summary();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canSeeCandidate(#id)")
    public CandidateDto get(@PathVariable Long id) {
        return candidates.get(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("@authz.canSeeCandidate(#id)")
    public List<HistoryEntryDto> history(@PathVariable Long id) {
        return candidates.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public CandidateDto create(@Valid @RequestBody CandidateRequest req) {
        return candidates.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public CandidateDto update(@PathVariable Long id, @Valid @RequestBody CandidateRequest req) {
        return candidates.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void delete(@PathVariable Long id) {
        candidates.delete(id);
    }

    /** Quyền theo ngạch của hồ sơ được kiểm tra trong ScreeningService. */
    @PutMapping("/{id}/screening")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public CandidateDto screen(@PathVariable Long id, @Valid @RequestBody ScreeningRequest req) {
        return screening.screen(id, req);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("mau-nhap-ung-vien.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importer.template());
    }

    /** {@code dryRun=true}: chỉ kiểm tra file và báo lỗi, không lưu. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ORGANIZER')")
    public ImportReport importFile(@RequestPart("file") MultipartFile file,
                                   @RequestParam(defaultValue = "false") boolean dryRun) {
        if (file.isEmpty()) {
            throw new BusinessException("IMPORT_EMPTY", "Chưa chọn file hoặc file rỗng");
        }
        try {
            return importer.importFile(file.getInputStream(), dryRun);
        } catch (IOException e) {
            throw new BusinessException("IMPORT_UNREADABLE", "Không đọc được file tải lên");
        }
    }
}
