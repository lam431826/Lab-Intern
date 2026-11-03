package vn.dxlab.recruitment.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.candidate.ScreeningStatus;
import vn.dxlab.recruitment.interview.InterviewSession;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.interview.InterviewSlot;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.scoring.Score;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.settings.ScoringSettings;
import vn.dxlab.recruitment.settings.ScoringSettingsRepository;
import vn.dxlab.recruitment.support.IntegrationTest;
import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.track.CriterionGroup;
import vn.dxlab.recruitment.track.CriterionRepository;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;
import vn.dxlab.recruitment.user.Role;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chạy toàn bộ migration trên SQL Server thật. Context khởi động được nghĩa là
 * Hibernate validate đã khớp mọi entity với schema.
 */
@IntegrationTest
@Transactional
class FlywayMigrationIT {

    @Autowired TrackRepository tracks;
    @Autowired CriterionRepository criteria;
    @Autowired ScoringSettingsRepository settings;
    @Autowired AppUserRepository users;
    @Autowired CandidateRepository candidates;
    @Autowired InterviewSessionRepository sessions;
    @Autowired InterviewSlotRepository slots;
    @Autowired ScoreRepository scores;
    @Autowired JdbcTemplate jdbc;

    @Test
    void seedsSixTracksInPlanOrder() {
        assertThat(tracks.findAllByOrderBySortOrderAsc())
                .extracting(Track::getCode)
                .containsExactly("AI", "GRAPHIC", "SE", "GAME_DEV", "IA", "GAME_DESIGN");
        assertThat(tracks.findAll()).extracting(Track::getQuota).containsOnly(3, 5, 8, 7, 3, 2)
                .satisfies(q -> assertThat(q.stream().mapToInt(Integer::intValue).sum()).isEqualTo(28));
    }

    @Test
    void seedsCriteriaFromInterviewDocument() {
        assertThat(criteria.findByGroupAndActiveTrueOrderBySortOrderAsc(CriterionGroup.GENERAL))
                .extracting(Criterion::getName)
                .containsExactly("Thái độ & Tinh thần học hỏi", "Tư duy logic & Giải quyết vấn đề",
                        "Tinh thần Teamwork", "Tư duy phản biện & Xử lý tình huống");

        Map<String, Long> professionalPerTrack = criteria.findAll().stream()
                .filter(c -> c.getGroup() == CriterionGroup.PROFESSIONAL)
                .collect(Collectors.groupingBy(c -> c.getTrack().getCode(), Collectors.counting()));
        assertThat(professionalPerTrack).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AI", 4L, "GRAPHIC", 4L, "SE", 5L, "GAME_DEV", 4L, "IA", 6L, "GAME_DESIGN", 4L));

        Track se = tracks.findByCode("SE").orElseThrow();
        List<Criterion> forSe = criteria.findApplicableToTrack(se.getId());
        assertThat(forSe).hasSize(9);
        assertThat(forSe.get(4).getName()).isEqualTo("Kiến thức lập trình cơ bản Java");
        assertThat(forSe.get(4).getRubricHigh()).startsWith("Đọc hiểu mã nguồn phức tạp hơn");
    }

    @Test
    void seedsDefaultScoringSettings() {
        ScoringSettings s = settings.findById(ScoringSettings.SINGLETON_ID).orElseThrow();
        assertThat(s.getWeightGeneral()).isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(s.getWeightProfessional()).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(s.getPassThreshold()).isEqualByComparingTo(new BigDecimal("7.00"));
        assertThat(s.getSlaHours()).isEqualTo(36);
        assertThat(s.getSlotMinutes()).isEqualTo(15);
    }

    @Test
    void keepsVietnameseTextAndSearchesWithoutAccents() {
        AppUser saved = users.saveAndFlush(user("anh@dxlab.vn", "Nguyễn Thị Ánh Tuyết"));
        users.saveAndFlush(user("binh@dxlab.vn", "Đặng Văn Bình"));

        assertThat(users.findById(saved.getId()).orElseThrow().getFullName()).isEqualTo("Nguyễn Thị Ánh Tuyết");
        assertThat(countUsersNamed("%nguyen thi anh%")).isEqualTo(1);
        assertThat(countUsersNamed("dang van binh")).isEqualTo(1);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private Integer countUsersNamed(String pattern) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE full_name LIKE ?", Integer.class, pattern);
    }

    @Test
    void rejectsScoreOutsideOneToTen() {
        Candidate candidate = candidates.saveAndFlush(candidate("SE0001", "SE"));
        Score score = new Score();
        score.setCandidate(candidate);
        score.setEvaluator(users.saveAndFlush(user("gk@dxlab.vn", "Giám khảo")));
        score.setCriterion(criteria.findApplicableToTrack(candidate.getTrack().getId()).getFirst());
        score.setValue(11);

        assertThatThrownBy(() -> scores.saveAndFlush(score)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresReasonWhenCandidateRejected() {
        Candidate candidate = candidate("AI0001", "AI");
        candidate.setScreeningStatus(ScreeningStatus.REJECTED);

        assertThatThrownBy(() -> candidates.saveAndFlush(candidate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOnlyOneActiveSlotPerCandidate() {
        Candidate candidate = candidates.saveAndFlush(candidate("IA0001", "IA"));
        InterviewSession session = new InterviewSession();
        session.setTrack(candidate.getTrack());
        session.setSessionDate(LocalDate.of(2026, 8, 11));
        session.setStartTime(LocalTime.of(16, 0));
        session.setEndTime(LocalTime.of(18, 30));
        sessions.saveAndFlush(session);

        slots.saveAndFlush(slot(session, candidate, LocalTime.of(16, 0)));

        assertThatThrownBy(() -> slots.saveAndFlush(slot(session, candidate, LocalTime.of(16, 15))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AppUser user(String email, String fullName) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(Role.MEMBER);
        return u;
    }

    private Candidate candidate(String studentCode, String trackCode) {
        Candidate c = new Candidate();
        c.setStudentCode(studentCode);
        c.setFullName("Ứng viên mẫu " + studentCode);
        c.setTrack(tracks.findByCode(trackCode).orElseThrow());
        return c;
    }

    private static InterviewSlot slot(InterviewSession session, Candidate candidate, LocalTime start) {
        InterviewSlot s = new InterviewSlot();
        s.setSession(session);
        s.setCandidate(candidate);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(15));
        return s;
    }
}
