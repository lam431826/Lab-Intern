package vn.dxlab.recruitment.interview;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.candidate.ScreeningStatus;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.interview.InterviewRules.TimeRange;
import vn.dxlab.recruitment.interview.dto.AttendanceRequest;
import vn.dxlab.recruitment.interview.dto.AutoScheduleRequest;
import vn.dxlab.recruitment.interview.dto.AutoScheduleResult;
import vn.dxlab.recruitment.interview.dto.RescheduleRequest;
import vn.dxlab.recruitment.interview.dto.SlotDto;
import vn.dxlab.recruitment.interview.dto.SlotRequest;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.security.AuthorizationService;
import vn.dxlab.recruitment.settings.SettingsService;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Slot phỏng vấn của từng ứng viên. Mỗi ứng viên có tối đa một slot đang hiệu lực (index lọc trong DB).
 * Dời lịch không sửa đè: slot PV1 giữ lại với {@code active = false} và trỏ sang slot PV2 mới,
 * để Interview Log còn thấy ai đã dời, dời từ đâu.
 */
@Service
@Transactional
public class SlotService {

    /** Lịch sử slot ghi theo hồ sơ ứng viên, cùng chỗ với lịch sử sàng lọc. */
    private static final String CANDIDATE_HISTORY = "CANDIDATE";

    private final InterviewSessionRepository sessions;
    private final InterviewSlotRepository slots;
    private final CandidateRepository candidates;
    private final ScoreRepository scores;
    private final SettingsService settings;
    private final SessionService sessionService;
    private final AuthorizationService authz;
    private final StatusHistoryService history;

    public SlotService(InterviewSessionRepository sessions, InterviewSlotRepository slots, CandidateRepository candidates,
                       ScoreRepository scores, SettingsService settings, SessionService sessionService,
                       AuthorizationService authz, StatusHistoryService history) {
        this.sessions = sessions;
        this.slots = slots;
        this.candidates = candidates;
        this.scores = scores;
        this.settings = settings;
        this.sessionService = sessionService;
        this.authz = authz;
        this.history = history;
    }

    /** Xếp một ứng viên vào ca, tại giờ chỉ định hoặc chỗ trống sớm nhất. */
    public SlotDto schedule(Long sessionId, SlotRequest req) {
        InterviewSession session = lock(sessionId);
        Candidate c = candidate(req.candidateId());
        checkSchedulable(c, session);
        InterviewSlot slot = place(session, c, req.startTime(), SlotType.PV1);
        history.record(CANDIDATE_HISTORY, c.getId(), "slot", null, describe(slot), "Xếp lịch");
        return SessionService.toSlotDto(slot);
    }

    /**
     * Xếp liên tiếp từ đầu ca, mỗi slot dài {@code slotMinutes} phút, bỏ qua giờ đã có người.
     * Ứng viên không vừa ca được trả về trong {@code notScheduled} để xếp sang ca khác.
     */
    public AutoScheduleResult autoSchedule(Long sessionId, AutoScheduleRequest req) {
        InterviewSession session = lock(sessionId);
        List<Candidate> queue;
        if (req.candidateIds().isEmpty()) {
            Set<Long> scheduled = slots.findCandidateIdsWithActiveSlot();
            queue = candidates.findByTrackIdAndScreeningStatus(session.getTrack().getId(), ScreeningStatus.PASSED).stream()
                    .filter(c -> !scheduled.contains(c.getId()))
                    .sorted(Comparator.comparing(Candidate::getScreenedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Candidate::getStudentCode))
                    .toList();
        } else {
            queue = req.candidateIds().stream().distinct().map(this::candidate).toList();
            queue.forEach(c -> checkSchedulable(c, session));
        }

        int minutes = settings.current().getSlotMinutes();
        List<TimeRange> free = InterviewRules.freeSlots(range(session), occupied(sessionId, null), minutes, queue.size());
        List<SlotDto> placed = new ArrayList<>();
        for (int i = 0; i < free.size(); i++) {
            InterviewSlot slot = newSlot(session, queue.get(i), free.get(i), SlotType.PV1);
            history.record(CANDIDATE_HISTORY, queue.get(i).getId(), "slot", null, describe(slot), "Tự xếp lịch");
            placed.add(SessionService.toSlotDto(slot));
        }
        List<String> left = queue.subList(free.size(), queue.size()).stream().map(Candidate::getStudentCode).toList();
        return new AutoScheduleResult(placed, left, sessionService.toDto(session));
    }

    /**
     * Dời lịch (PV1 sang PV2), kể cả khi ứng viên vắng PV1. Mỗi ứng viên chỉ dời một lần;
     * đã điểm danh có mặt thì không dời.
     */
    public SlotDto reschedule(Long slotId, RescheduleRequest req) {
        InterviewSlot old = slot(slotId);
        requireActive(old);
        if (old.getAttendance() == Attendance.ATTENDED) {
            throw BusinessException.conflict("SLOT_ATTENDED", "Ứng viên đã phỏng vấn ở slot này, không dời lịch được");
        }
        if (old.getSlotType() == SlotType.PV2) {
            throw new BusinessException("RESCHEDULE_LIMIT", "Ứng viên đã dời lịch một lần (PV2), không dời tiếp được");
        }
        InterviewSession target = lock(req.sessionId());
        if (!target.getTrack().getId().equals(old.getCandidate().getTrack().getId())) {
            throw new BusinessException("TRACK_MISMATCH", "Ca mới phải thuộc ngạch của ứng viên");
        }
        TimeRange time = pickTime(target, req.startTime(), old.getId());

        // Tắt slot cũ trước khi tạo slot mới: index "mỗi ứng viên một slot active"
        old.setActive(false);
        old.setNote(req.reason().trim());
        slots.saveAndFlush(old);
        InterviewSlot pv2 = newSlot(target, old.getCandidate(), time, SlotType.PV2);
        old.setReplacedBy(pv2);
        slots.saveAndFlush(old);
        history.record(CANDIDATE_HISTORY, old.getCandidate().getId(), "slot", describe(old), describe(pv2), req.reason().trim());
        return SessionService.toSlotDto(pv2);
    }

    /**
     * Hủy slot chưa diễn ra. Hủy slot PV2 thì slot PV1 trước đó hiệu lực trở lại;
     * hủy slot PV1 thì ứng viên về trạng thái chưa có lịch.
     */
    public void cancel(Long slotId) {
        InterviewSlot slot = slot(slotId);
        requireActive(slot);
        if (slot.getAttendance() != Attendance.PENDING) {
            throw BusinessException.conflict("SLOT_ATTENDED", "Slot đã điểm danh, không hủy được");
        }
        Candidate c = slot.getCandidate();
        if (scores.existsByCandidateId(c.getId())) {
            throw BusinessException.conflict("CANDIDATE_IN_USE", "Ứng viên đã có điểm, không hủy lịch được");
        }
        lock(slot.getSession().getId());
        InterviewSlot previous = slots.findByReplacedById(slotId).orElse(null);
        String description = describe(slot);
        if (previous != null) {
            previous.setReplacedBy(null);
            slots.saveAndFlush(previous);
        }
        slots.delete(slot);
        slots.flush();
        if (previous != null) {
            previous.setActive(true);
            slots.saveAndFlush(previous);
        }
        history.record(CANDIDATE_HISTORY, c.getId(), "slot", description,
                previous == null ? null : describe(previous), "Hủy lịch");
    }

    /** Điểm danh. Đã có điểm thì không đổi khỏi "có mặt" được. */
    public SlotDto attendance(Long slotId, AttendanceRequest req) {
        InterviewSlot slot = slot(slotId);
        if (!authz.canRunSession(slot.getSession().getId())) {
            throw new AccessDeniedException("Chỉ Người tổ chức hoặc thành viên hội đồng của ca được điểm danh");
        }
        requireActive(slot);
        Long candidateId = slot.getCandidate().getId();
        if (slot.getAttendance() == Attendance.ATTENDED && req.attendance() != Attendance.ATTENDED
                && scores.existsByCandidateId(candidateId)) {
            throw BusinessException.conflict("ATTENDANCE_LOCKED", "Ứng viên đã có điểm, không đổi điểm danh được");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        history.record(CANDIDATE_HISTORY, candidateId, "attendance", slot.getAttendance(), req.attendance(), note);
        slot.setAttendance(req.attendance());
        slot.setNote(note);
        slots.saveAndFlush(slot);
        return SessionService.toSlotDto(slot);
    }

    @Transactional(readOnly = true)
    public List<SlotDto> ofCandidate(Long candidateId) {
        return slots.findByCandidateIdOrderByIdAsc(candidateId).stream().map(SessionService::toSlotDto).toList();
    }

    private InterviewSlot place(InterviewSession session, Candidate c, LocalTime start, SlotType type) {
        return newSlot(session, c, pickTime(session, start, null), type);
    }

    /** Giờ cho slot mới: giờ chỉ định (phải nằm trong ca, không trùng slot khác) hoặc chỗ trống sớm nhất. */
    private TimeRange pickTime(InterviewSession session, LocalTime start, Long ignoreSlotId) {
        int minutes = settings.current().getSlotMinutes();
        List<TimeRange> taken = occupied(session.getId(), ignoreSlotId);
        if (start == null) {
            return InterviewRules.freeSlots(range(session), taken, minutes, 1).stream().findFirst()
                    .orElseThrow(() -> BusinessException.conflict("SESSION_FULL", "Ca đã kín lịch"));
        }
        TimeRange wanted = new TimeRange(start, start.plusMinutes(minutes));
        if (wanted.end().isBefore(start) || !range(session).contains(wanted.start(), wanted.end())) {
            throw new BusinessException("SLOT_OUTSIDE_SESSION", "Slot " + InterviewRules.label(wanted.start(), wanted.end())
                    + " nằm ngoài giờ của ca " + InterviewRules.label(session.getStartTime(), session.getEndTime()));
        }
        if (taken.stream().anyMatch(t -> t.overlaps(wanted.start(), wanted.end()))) {
            throw BusinessException.conflict("SLOT_OVERLAP", "Giờ " + InterviewRules.label(wanted.start(), wanted.end())
                    + " đã có ứng viên khác");
        }
        return wanted;
    }

    private InterviewSlot newSlot(InterviewSession session, Candidate c, TimeRange time, SlotType type) {
        InterviewSlot slot = new InterviewSlot();
        slot.setSession(session);
        slot.setCandidate(c);
        slot.setStartTime(time.start());
        slot.setEndTime(time.end());
        slot.setSlotType(type);
        return slots.saveAndFlush(slot);
    }

    private List<TimeRange> occupied(Long sessionId, Long ignoreSlotId) {
        return slots.findBySessionIdAndActiveTrueOrderByStartTimeAsc(sessionId).stream()
                .filter(s -> !Objects.equals(s.getId(), ignoreSlotId))
                .map(s -> new TimeRange(s.getStartTime(), s.getEndTime()))
                .collect(Collectors.toList());
    }

    private void checkSchedulable(Candidate c, InterviewSession session) {
        if (c.getScreeningStatus() != ScreeningStatus.PASSED) {
            throw new BusinessException("CANDIDATE_NOT_PASSED", "Hồ sơ " + c.getStudentCode() + " chưa Đạt sàng lọc CV");
        }
        if (!c.getTrack().getId().equals(session.getTrack().getId())) {
            throw new BusinessException("TRACK_MISMATCH", "Hồ sơ " + c.getStudentCode() + " không thuộc ngạch của ca");
        }
        if (slots.existsByCandidateIdAndActiveTrue(c.getId())) {
            throw BusinessException.conflict("CANDIDATE_ALREADY_SCHEDULED",
                    "Hồ sơ " + c.getStudentCode() + " đã có lịch. Dùng chức năng dời lịch nếu cần đổi giờ.");
        }
    }

    private static void requireActive(InterviewSlot slot) {
        if (!slot.isActive()) {
            throw BusinessException.conflict("SLOT_INACTIVE", "Slot này đã được dời sang lịch khác");
        }
    }

    private static TimeRange range(InterviewSession s) {
        return new TimeRange(s.getStartTime(), s.getEndTime());
    }

    private static String describe(InterviewSlot slot) {
        return slot.getSlotType() + " " + slot.getSession().getSessionDate() + " "
                + InterviewRules.label(slot.getStartTime(), slot.getEndTime());
    }

    private InterviewSession lock(Long sessionId) {
        return sessions.lockById(sessionId).orElseThrow(() -> new NotFoundException("ca phỏng vấn", sessionId));
    }

    private Candidate candidate(Long id) {
        return candidates.findById(id).orElseThrow(() -> new NotFoundException("hồ sơ", id));
    }

    private InterviewSlot slot(Long id) {
        return slots.findById(id).orElseThrow(() -> new NotFoundException("slot", id));
    }
}
