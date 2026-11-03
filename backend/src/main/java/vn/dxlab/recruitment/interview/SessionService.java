package vn.dxlab.recruitment.interview;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.interview.InterviewRules.TimeRange;
import vn.dxlab.recruitment.interview.dto.LogRequest;
import vn.dxlab.recruitment.interview.dto.PanelMemberDto;
import vn.dxlab.recruitment.interview.dto.PanelRequest;
import vn.dxlab.recruitment.interview.dto.SessionDto;
import vn.dxlab.recruitment.interview.dto.SessionRequest;
import vn.dxlab.recruitment.interview.dto.SessionWarning;
import vn.dxlab.recruitment.interview.dto.SlotDto;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.security.AppUserPrincipal;
import vn.dxlab.recruitment.security.AuthorizationService;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;
import vn.dxlab.recruitment.user.Role;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ca phỏng vấn và hội đồng (Phân hệ 2). Hội đồng chuẩn 6 người: Giảng viên và Người tổ chức cố định,
 * Lead ngạch, 3 thành viên luân chuyển. Sai chuẩn chỉ cảnh báo, không chặn, vì lab có thể thiếu người.
 */
@Service
@Transactional
public class SessionService {

    static final String HISTORY_TYPE = "SESSION";

    private final InterviewSessionRepository sessions;
    private final InterviewSlotRepository slots;
    private final TrackRepository tracks;
    private final AppUserRepository users;
    private final ScoreRepository scores;
    private final AuthorizationService authz;
    private final CurrentUser currentUser;
    private final StatusHistoryService history;

    public SessionService(InterviewSessionRepository sessions, InterviewSlotRepository slots, TrackRepository tracks,
                          AppUserRepository users, ScoreRepository scores, AuthorizationService authz,
                          CurrentUser currentUser, StatusHistoryService history) {
        this.sessions = sessions;
        this.slots = slots;
        this.tracks = tracks;
        this.users = users;
        this.scores = scores;
        this.authz = authz;
        this.currentUser = currentUser;
        this.history = history;
    }

    /** Giảng viên, Người tổ chức thấy mọi ca; Lead thấy ca ngạch mình và ca mình ngồi hội đồng; Thành viên chỉ thấy ca mình ngồi. */
    @Transactional(readOnly = true)
    public List<SessionDto> list(Long trackId, LocalDate date) {
        AppUserPrincipal me = currentUser.get();
        Stream<InterviewSession> visible = switch (me.role()) {
            case ORGANIZER, LECTURER -> sessions.findAllByOrderBySessionDateAscStartTimeAsc().stream();
            case LEAD -> {
                Set<Long> own = tracks.findByLeadId(me.id()).stream().map(Track::getId).collect(Collectors.toSet());
                yield sessions.findAllByOrderBySessionDateAscStartTimeAsc().stream()
                        .filter(s -> own.contains(s.getTrack().getId())
                                || s.getPanel().stream().anyMatch(p -> p.getUser().getId().equals(me.id())));
            }
            case MEMBER -> sessions.findWherePanelMember(me.id()).stream();
        };
        return visible
                .filter(s -> trackId == null || s.getTrack().getId().equals(trackId))
                .filter(s -> date == null || s.getSessionDate().equals(date))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDto get(Long id) {
        return toDto(find(id));
    }

    public SessionDto create(SessionRequest req) {
        Track track = track(req.trackId());
        validateTime(req);
        InterviewSession s = new InterviewSession();
        s.setTrack(track);
        applyTime(s, req);

        // Thành viên cố định: mọi Giảng viên đang hoạt động, Người tổ chức tạo ca, Lead ngạch
        users.findByRoleAndActiveTrueOrderByIdAsc(Role.LECTURER).forEach(u -> addMember(s, u, PanelRole.LECTURER));
        addMember(s, users.getReferenceById(currentUser.id()), PanelRole.ORGANIZER);
        if (track.getLead() != null && track.getLead().isActive()) {
            addMember(s, track.getLead(), PanelRole.LEAD);
        }
        for (AppUser u : activeUsers(req.rotatingMemberIds())) {
            addMember(s, u, PanelRole.ROTATING);
        }
        sessions.saveAndFlush(s);
        return toDto(s);
    }

    /** Sửa ngày giờ, phòng. Không đổi ngạch khi ca đã có slot; giờ mới phải chứa hết các slot đang hiệu lực. */
    public SessionDto update(Long id, SessionRequest req) {
        InterviewSession s = sessions.lockById(id).orElseThrow(() -> new NotFoundException("ca phỏng vấn", id));
        validateTime(req);
        if (!s.getTrack().getId().equals(req.trackId())) {
            if (slots.existsBySessionId(id)) {
                throw BusinessException.conflict("SESSION_IN_USE", "Ca đã có lịch của ứng viên, không đổi ngạch được");
            }
            s.setTrack(track(req.trackId()));
        }
        TimeRange range = new TimeRange(req.startTime(), req.endTime());
        boolean slotOutside = slots.findBySessionIdAndActiveTrueOrderByStartTimeAsc(id).stream()
                .anyMatch(slot -> !range.contains(slot.getStartTime(), slot.getEndTime()));
        if (slotOutside) {
            throw new BusinessException("SLOT_OUTSIDE_SESSION",
                    "Giờ mới của ca không chứa hết các slot đã xếp. Dời hoặc hủy slot trước.");
        }
        applyTime(s, req);
        sessions.saveAndFlush(s);
        return toDto(s);
    }

    public void delete(Long id) {
        InterviewSession s = find(id);
        if (slots.existsBySessionId(id)) {
            throw BusinessException.conflict("SESSION_IN_USE", "Ca đã có lịch của ứng viên. Hủy hoặc dời các slot trước khi xóa ca.");
        }
        sessions.delete(s);
    }

    /**
     * Thay thành viên luân chuyển. Thành viên đã chấm điểm trong ca thì không bỏ ra được.
     * Lead hiện tại của ngạch được thêm lại nếu đang thiếu (ví dụ ngạch vừa đổi Lead).
     */
    public SessionDto updatePanel(Long id, PanelRequest req) {
        InterviewSession s = find(id);
        Set<Long> wanted = new LinkedHashSet<>(req.rotatingMemberIds());
        List<PanelMember> removed = s.getPanel().stream()
                .filter(p -> p.getPanelRole() == PanelRole.ROTATING && !wanted.contains(p.getUser().getId()))
                .toList();
        Set<Long> candidateIds = slots.findBySessionIdOrderByStartTimeAsc(id).stream()
                .map(slot -> slot.getCandidate().getId()).collect(Collectors.toSet());
        for (PanelMember p : removed) {
            if (!candidateIds.isEmpty() && scores.existsByEvaluatorIdAndCandidateIdIn(p.getUser().getId(), candidateIds)) {
                throw BusinessException.conflict("PANEL_MEMBER_HAS_SCORES",
                        p.getUser().getFullName() + " đã chấm điểm trong ca này, không bỏ khỏi hội đồng được");
            }
        }
        s.getPanel().removeAll(removed);
        // Xóa trước khi thêm để không vướng UNIQUE(session_id, user_id) khi đổi vai trò
        sessions.flush();
        for (AppUser u : activeUsers(List.copyOf(wanted))) {
            addMember(s, u, PanelRole.ROTATING);
        }
        AppUser lead = s.getTrack().getLead();
        if (lead != null && lead.isActive()) {
            addMember(s, lead, PanelRole.LEAD);
        }
        history.record(HISTORY_TYPE, id, "panel", null,
                s.getPanel().stream().map(p -> p.getUser().getFullName()).collect(Collectors.joining(", ")), null);
        sessions.saveAndFlush(s);
        return toDto(s);
    }

    /** Interview Log: nhận xét chung về chất lượng ca. */
    public SessionDto updateLog(Long id, LogRequest req) {
        InterviewSession s = find(id);
        if (!authz.canRunSession(id)) {
            throw new AccessDeniedException("Chỉ Người tổ chức hoặc thành viên hội đồng của ca được ghi Interview Log");
        }
        s.setLogSummary(req.logSummary() == null || req.logSummary().isBlank() ? null : req.logSummary().trim());
        sessions.saveAndFlush(s);
        return toDto(s);
    }

    InterviewSession find(Long id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("ca phỏng vấn", id));
    }

    SessionDto toDto(InterviewSession s) {
        Track t = s.getTrack();
        List<PanelMemberDto> panel = s.getPanel().stream()
                .sorted(Comparator.comparing((PanelMember p) -> p.getPanelRole().ordinal())
                        .thenComparing(p -> p.getUser().getFullName()))
                .map(p -> new PanelMemberDto(p.getUser().getId(), p.getUser().getFullName(), p.getUser().getEmail(), p.getPanelRole()))
                .toList();
        List<SlotDto> slotDtos = slots.findBySessionIdOrderByStartTimeAsc(s.getId()).stream()
                .sorted(Comparator.comparing(InterviewSlot::getStartTime).thenComparing(slot -> !slot.isActive()))
                .map(SessionService::toSlotDto)
                .toList();
        return new SessionDto(s.getId(), t.getId(), t.getCode(), t.getName(), s.getSessionDate(), s.getStartTime(),
                s.getEndTime(), s.getRoom(), s.getLogSummary(), panel, slotDtos, warnings(s));
    }

    static SlotDto toSlotDto(InterviewSlot slot) {
        InterviewSession s = slot.getSession();
        return new SlotDto(slot.getId(), s.getId(), s.getSessionDate(), s.getRoom(),
                slot.getCandidate().getId(), slot.getCandidate().getStudentCode(), slot.getCandidate().getFullName(),
                slot.getStartTime(), slot.getEndTime(), InterviewRules.label(slot.getStartTime(), slot.getEndTime()),
                slot.getSlotType(), slot.isActive(), slot.getAttendance(), slot.getNote(),
                slot.getReplacedBy() == null ? null : slot.getReplacedBy().getId());
    }

    private List<SessionWarning> warnings(InterviewSession s) {
        List<SessionWarning> result = new ArrayList<>();
        if (!InterviewRules.withinWindows(s.getStartTime(), s.getEndTime())) {
            result.add(new SessionWarning("OUTSIDE_HOURS",
                    "Ca nằm ngoài khung giờ phỏng vấn (10h–12h30, 16h–18h30)"));
        }
        if (s.getPanel().size() != InterviewRules.PANEL_SIZE) {
            result.add(new SessionWarning("PANEL_SIZE",
                    "Hội đồng có " + s.getPanel().size() + " người, quy định " + InterviewRules.PANEL_SIZE));
        }
        AppUser lead = s.getTrack().getLead();
        if (lead == null) {
            result.add(new SessionWarning("LEAD_MISSING", "Ngạch " + s.getTrack().getName() + " chưa có Lead"));
        } else if (s.getPanel().stream().noneMatch(p -> p.getUser().getId().equals(lead.getId()))) {
            result.add(new SessionWarning("LEAD_MISSING", "Lead ngạch " + lead.getFullName() + " không có trong hội đồng"));
        }
        TimeRange range = new TimeRange(s.getStartTime(), s.getEndTime());
        Set<Long> panelIds = s.getPanel().stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
        for (InterviewSession other : sessions.findBySessionDateOrderByStartTimeAsc(s.getSessionDate())) {
            if (Objects.equals(other.getId(), s.getId()) || !range.overlaps(other.getStartTime(), other.getEndTime())) {
                continue;
            }
            other.getPanel().stream().filter(p -> panelIds.contains(p.getUser().getId())).forEach(p ->
                    result.add(new SessionWarning("PANEL_BUSY", p.getUser().getFullName() + " cũng ngồi ca "
                            + other.getTrack().getName() + " " + InterviewRules.label(other.getStartTime(), other.getEndTime())
                            + " trùng giờ")));
        }
        long trackSlots = slots.countBySessionTrackIdAndActiveTrue(s.getTrack().getId());
        if (trackSlots > s.getTrack().getSlotMax()) {
            result.add(new SessionWarning("TRACK_SLOTS_OVER_MAX", "Ngạch " + s.getTrack().getName() + " đã xếp "
                    + trackSlots + " slot, vượt mức " + s.getTrack().getSlotMax() + " theo Kế hoạch"));
        }
        return result;
    }

    private static void addMember(InterviewSession s, AppUser user, PanelRole role) {
        if (s.getPanel().stream().anyMatch(p -> p.getUser().getId().equals(user.getId()))) {
            return;
        }
        PanelMember p = new PanelMember();
        p.setSession(s);
        p.setUser(user);
        p.setPanelRole(role);
        s.getPanel().add(p);
    }

    private List<AppUser> activeUsers(List<Long> ids) {
        List<AppUser> found = users.findAllById(ids);
        Set<Long> foundIds = found.stream().map(AppUser::getId).collect(Collectors.toSet());
        ids.stream().filter(id -> !foundIds.contains(id)).findFirst().ifPresent(id -> {
            throw new NotFoundException("tài khoản", id);
        });
        found.stream().filter(u -> !u.isActive()).findFirst().ifPresent(u -> {
            throw new BusinessException("USER_INACTIVE", "Tài khoản " + u.getFullName() + " đã bị khóa");
        });
        // Giữ thứ tự gửi lên
        return ids.stream().distinct()
                .map(id -> found.stream().filter(u -> u.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    private static void validateTime(SessionRequest req) {
        if (!req.startTime().isBefore(req.endTime())) {
            throw new BusinessException("INVALID_SESSION_TIME", "Giờ kết thúc phải sau giờ bắt đầu");
        }
    }

    private static void applyTime(InterviewSession s, SessionRequest req) {
        s.setSessionDate(req.sessionDate());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setRoom(req.room());
    }

    private Track track(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("ngạch", id));
    }
}
