package vn.dxlab.recruitment.result;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.dxlab.recruitment.result.dto.DashboardDto;
import vn.dxlab.recruitment.result.dto.DecisionDto;
import vn.dxlab.recruitment.result.dto.DecisionRequest;
import vn.dxlab.recruitment.result.dto.OfferRequest;
import vn.dxlab.recruitment.result.dto.TrackResultDto;
import vn.dxlab.recruitment.result.dto.VersionRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Kết quả, kết luận, phê duyệt, offer (Phân hệ 7) và dashboard. */
@RestController
public class ResultController {

    private final ResultService results;
    private final DecisionService decisions;
    private final ResultExportService exporter;
    private final DashboardService dashboard;

    public ResultController(ResultService results, DecisionService decisions, ResultExportService exporter,
                            DashboardService dashboard) {
        this.results = results;
        this.decisions = decisions;
        this.exporter = exporter;
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    public DashboardDto dashboard() {
        return dashboard.get();
    }

    @GetMapping("/api/results")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER', 'LEAD')")
    public List<TrackResultDto> list() {
        return results.list();
    }

    @GetMapping("/api/results/{trackId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER', 'LEAD')")
    public TrackResultDto track(@PathVariable Long trackId) {
        return results.track(trackId);
    }

    @PostMapping("/api/results/{trackId}/approve")
    @PreAuthorize("hasRole('LECTURER')")
    public TrackResultDto approveTrack(@PathVariable Long trackId) {
        return decisions.approveTrack(trackId);
    }

    @GetMapping("/api/results/export")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long trackId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("ket-qua-tuyen-dung.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exporter.export(trackId));
    }

    @PutMapping("/api/candidates/{id}/decision")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER')")
    public DecisionDto decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest req) {
        return decisions.decide(id, req);
    }

    @PostMapping("/api/candidates/{id}/decision/approve")
    @PreAuthorize("hasRole('LECTURER')")
    public DecisionDto approve(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return decisions.approve(id, req.version());
    }

    @PostMapping("/api/candidates/{id}/decision/reopen")
    @PreAuthorize("hasRole('LECTURER')")
    public DecisionDto reopen(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return decisions.reopen(id, req.version());
    }

    @PutMapping("/api/candidates/{id}/decision/offer")
    @PreAuthorize("hasRole('ORGANIZER')")
    public DecisionDto offer(@PathVariable Long id, @Valid @RequestBody OfferRequest req) {
        return decisions.offer(id, req);
    }

    @PostMapping("/api/candidates/{id}/decision/promote")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER')")
    public DecisionDto promote(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return decisions.promote(id, req.version());
    }
}
