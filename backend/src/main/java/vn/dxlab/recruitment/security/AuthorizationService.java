package vn.dxlab.recruitment.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.interview.Attendance;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.interview.PanelMemberRepository;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.Role;

/**
 * Quyền phụ thuộc dữ liệu (theo ngạch, theo ca), dùng trong
 * {@code @PreAuthorize("@authz.canScore(#candidateId)")} và gọi lại trong service.
 *
 * <p>Đây là chỗ labintern bị sót: quyền chấm điểm phải gắn với hội đồng của ca,
 * không suy ra từ vai trò.
 */
@Service("authz")
@Transactional(readOnly = true)
public class AuthorizationService {

    private final CurrentUser currentUser;
    private final PanelMemberRepository panelMembers;
    private final InterviewSlotRepository slots;
    private final CandidateRepository candidates;
    private final TrackRepository tracks;
    private final InterviewSessionRepository sessions;

    public AuthorizationService(CurrentUser currentUser, PanelMemberRepository panelMembers,
                                InterviewSlotRepository slots, CandidateRepository candidates, TrackRepository tracks,
                                InterviewSessionRepository sessions) {
        this.currentUser = currentUser;
        this.panelMembers = panelMembers;
        this.slots = slots;
        this.candidates = candidates;
        this.tracks = tracks;
        this.sessions = sessions;
    }

    /** Giảng viên hoặc Người tổ chức: xem được mọi ngạch. */
    public boolean isStaffAdmin() {
        Role role = currentUser.get().role();
        return role == Role.LECTURER || role == Role.ORGANIZER;
    }

    /** Người dùng là Lead được gán cho ngạch này. */
    public boolean isLeadOf(Long trackId) {
        AppUserPrincipal me = currentUser.get();
        return me.role() == Role.LEAD && tracks.findById(trackId)
                .map(t -> t.getLead() != null && t.getLead().getId().equals(me.id()))
                .orElse(false);
    }

    public boolean isPanelMember(Long sessionId) {
        return panelMembers.existsBySessionIdAndUserId(sessionId, currentUser.id());
    }

    /**
     * Chấm được khi: ứng viên có slot đang hiệu lực, đã được điểm danh có mặt,
     * và người chấm có tên trong hội đồng của ca đó. Áp dụng cho mọi vai trò, kể cả Giảng viên.
     */
    public boolean canScore(Long candidateId) {
        return slots.findByCandidateIdAndActiveTrue(candidateId)
                .filter(slot -> slot.getAttendance() == Attendance.ATTENDED)
                .map(slot -> isPanelMember(slot.getSession().getId()))
                .orElse(false);
    }

    /** Xem hồ sơ: Giảng viên, Người tổ chức, Lead của ngạch, hoặc thành viên hội đồng của ca có ứng viên. */
    public boolean canSeeCandidate(Long candidateId) {
        if (isStaffAdmin()) {
            return true;
        }
        return candidates.findById(candidateId)
                .map(c -> isLeadOf(c.getTrack().getId())
                        || slots.findByCandidateIdAndActiveTrue(candidateId)
                                .map(slot -> isPanelMember(slot.getSession().getId()))
                                .orElse(false))
                .orElse(false);
    }

    /** Duyệt CV: Người tổ chức, hoặc Lead của ngạch. */
    public boolean canScreenTrack(Long trackId) {
        return currentUser.get().role() == Role.ORGANIZER || isLeadOf(trackId);
    }

    /** Xem ca: Giảng viên, Người tổ chức, Lead của ngạch, hoặc thành viên hội đồng của ca. */
    public boolean canSeeSession(Long sessionId) {
        if (isStaffAdmin() || isPanelMember(sessionId)) {
            return true;
        }
        return sessions.findById(sessionId).map(s -> isLeadOf(s.getTrack().getId())).orElse(false);
    }

    /** Điểm danh, ghi Interview Log: Người tổ chức hoặc thành viên hội đồng của ca. */
    public boolean canRunSession(Long sessionId) {
        return currentUser.get().role() == Role.ORGANIZER || isPanelMember(sessionId);
    }
}
