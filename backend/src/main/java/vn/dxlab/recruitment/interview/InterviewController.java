package vn.dxlab.recruitment.interview;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.dxlab.recruitment.interview.dto.AttendanceRequest;
import vn.dxlab.recruitment.interview.dto.AutoScheduleRequest;
import vn.dxlab.recruitment.interview.dto.AutoScheduleResult;
import vn.dxlab.recruitment.interview.dto.LogRequest;
import vn.dxlab.recruitment.interview.dto.PanelRequest;
import vn.dxlab.recruitment.interview.dto.RescheduleRequest;
import vn.dxlab.recruitment.interview.dto.SessionDto;
import vn.dxlab.recruitment.interview.dto.SessionRequest;
import vn.dxlab.recruitment.interview.dto.SlotDto;
import vn.dxlab.recruitment.interview.dto.SlotRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Lịch phỏng vấn (Phân hệ 2). Xếp lịch do Người tổ chức; điểm danh và log do hội đồng của ca. */
@RestController
public class InterviewController {

    private final SessionService sessions;
    private final SlotService slots;
    private final ScheduleExportService exporter;

    public InterviewController(SessionService sessions, SlotService slots, ScheduleExportService exporter) {
        this.sessions = sessions;
        this.slots = slots;
        this.exporter = exporter;
    }

    @GetMapping("/api/sessions")
    public List<SessionDto> list(@RequestParam(required = false) Long trackId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return sessions.list(trackId, date);
    }

    @GetMapping("/api/sessions/{id}")
    @PreAuthorize("@authz.canSeeSession(#id)")
    public SessionDto get(@PathVariable Long id) {
        return sessions.get(id);
    }

    @PostMapping("/api/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public SessionDto create(@Valid @RequestBody SessionRequest req) {
        return sessions.create(req);
    }

    @PutMapping("/api/sessions/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public SessionDto update(@PathVariable Long id, @Valid @RequestBody SessionRequest req) {
        return sessions.update(id, req);
    }

    @DeleteMapping("/api/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void delete(@PathVariable Long id) {
        sessions.delete(id);
    }

    @PutMapping("/api/sessions/{id}/panel")
    @PreAuthorize("hasRole('ORGANIZER')")
    public SessionDto panel(@PathVariable Long id, @Valid @RequestBody PanelRequest req) {
        return sessions.updatePanel(id, req);
    }

    @PutMapping("/api/sessions/{id}/log")
    @PreAuthorize("@authz.canRunSession(#id)")
    public SessionDto log(@PathVariable Long id, @Valid @RequestBody LogRequest req) {
        return sessions.updateLog(id, req);
    }

    @PostMapping("/api/sessions/{id}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public SlotDto schedule(@PathVariable Long id, @Valid @RequestBody SlotRequest req) {
        return slots.schedule(id, req);
    }

    @PostMapping("/api/sessions/{id}/auto-schedule")
    @PreAuthorize("hasRole('ORGANIZER')")
    public AutoScheduleResult autoSchedule(@PathVariable Long id, @RequestBody(required = false) AutoScheduleRequest req) {
        return slots.autoSchedule(id, req == null ? new AutoScheduleRequest(null) : req);
    }

    @PostMapping("/api/slots/{id}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public SlotDto reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest req) {
        return slots.reschedule(id, req);
    }

    @DeleteMapping("/api/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void cancel(@PathVariable Long id) {
        slots.cancel(id);
    }

    @PutMapping("/api/slots/{id}/attendance")
    public SlotDto attendance(@PathVariable Long id, @Valid @RequestBody AttendanceRequest req) {
        return slots.attendance(id, req);
    }

    @GetMapping("/api/candidates/{id}/slots")
    @PreAuthorize("@authz.canSeeCandidate(#id)")
    public List<SlotDto> candidateSlots(@PathVariable Long id) {
        return slots.ofCandidate(id);
    }

    @GetMapping("/api/sessions/export")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long trackId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("lich-phong-van.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exporter.export(trackId, from, to));
    }
}
