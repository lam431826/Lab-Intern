package vn.dxlab.recruitment.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.interview.Attendance;
import vn.dxlab.recruitment.interview.InterviewSession;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.interview.InterviewSlot;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.interview.PanelMember;
import vn.dxlab.recruitment.interview.PanelRole;
import vn.dxlab.recruitment.support.IntegrationTest;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;
import vn.dxlab.recruitment.user.Role;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Quyền theo ca và theo ngạch: chỗ labintern để lọt (ai đăng nhập cũng nộp được điểm). */
@IntegrationTest
@Transactional
class AuthorizationServiceIT {

    @Autowired AuthorizationService authz;
    @Autowired AppUserRepository users;
    @Autowired TrackRepository tracks;
    @Autowired CandidateRepository candidates;
    @Autowired InterviewSessionRepository sessions;
    @Autowired InterviewSlotRepository slots;

    AppUser panelist;
    AppUser outsider;
    AppUser aiLead;
    Track ai;
    Candidate candidate;
    InterviewSlot slot;

    @BeforeEach
    void setUp() {
        panelist = user("panel@dxlab.vn", Role.MEMBER);
        outsider = user("outside@dxlab.vn", Role.MEMBER);
        aiLead = user("lead@dxlab.vn", Role.LEAD);

        ai = tracks.findByCode("AI").orElseThrow();
        ai.setLead(aiLead);

        candidate = new Candidate();
        candidate.setStudentCode("AI9001");
        candidate.setFullName("Ứng viên mẫu AI");
        candidate.setTrack(ai);
        candidates.saveAndFlush(candidate);

        InterviewSession session = new InterviewSession();
        session.setTrack(ai);
        session.setSessionDate(LocalDate.of(2026, 8, 10));
        session.setStartTime(LocalTime.of(10, 0));
        session.setEndTime(LocalTime.of(11, 0));
        PanelMember member = new PanelMember();
        member.setSession(session);
        member.setUser(panelist);
        member.setPanelRole(PanelRole.ROTATING);
        session.getPanel().add(member);
        sessions.saveAndFlush(session);

        slot = new InterviewSlot();
        slot.setSession(session);
        slot.setCandidate(candidate);
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 15));
        slots.saveAndFlush(slot);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void panelMemberCannotScoreBeforeAttendanceIsTaken() {
        loginAs(panelist);
        assertThat(authz.canScore(candidate.getId())).isFalse();
    }

    @Test
    void panelMemberCanScoreAttendedCandidate() {
        markAttended();
        loginAs(panelist);
        assertThat(authz.canScore(candidate.getId())).isTrue();
    }

    @Test
    void memberOutsidePanelCannotScore() {
        markAttended();
        loginAs(outsider);
        assertThat(authz.canScore(candidate.getId())).isFalse();
        assertThat(authz.canSeeCandidate(candidate.getId())).isFalse();
    }

    @Test
    void absentCandidateCannotBeScored() {
        slot.setAttendance(Attendance.ABSENT);
        slots.saveAndFlush(slot);
        loginAs(panelist);
        assertThat(authz.canScore(candidate.getId())).isFalse();
    }

    @Test
    void leadSeesOnlyOwnTrack() {
        loginAs(aiLead);
        assertThat(authz.isLeadOf(ai.getId())).isTrue();
        assertThat(authz.isLeadOf(tracks.findByCode("SE").orElseThrow().getId())).isFalse();
        assertThat(authz.canSeeCandidate(candidate.getId())).isTrue();
        assertThat(authz.canScreenTrack(ai.getId())).isTrue();
    }

    @Test
    void leadOutsidePanelStillCannotScore() {
        markAttended();
        loginAs(aiLead);
        assertThat(authz.canScore(candidate.getId())).isFalse();
    }

    private void markAttended() {
        slot.setAttendance(Attendance.ATTENDED);
        slots.saveAndFlush(slot);
    }

    private AppUser user(String email, Role role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(email);
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    private static void loginAs(AppUser u) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("user_" + u.getId()).build();
        SecurityContextHolder.getContext().setAuthentication(new AppUserAuthentication(
                new AppUserPrincipal(u.getId(), u.getEmail(), u.getFullName(), u.getRole()), jwt));
    }
}
