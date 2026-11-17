package vn.dxlab.recruitment.migration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.interview.InterviewSession;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.support.IntegrationTest;
import vn.dxlab.recruitment.track.TrackRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày, giờ "địa phương" (LocalDate, LocalTime: ngày ca, giờ slot) phải được lưu đúng nguyên giá trị,
 * không bị đổi múi giờ. Lỗi cũ: hibernate.jdbc.time_zone=UTC làm 10:00 lưu thành 03:00 và ngày 20/11
 * thành 19/11 khi JVM chạy múi giờ Việt Nam (qua ứng dụng vẫn đúng vì lệch cả hai chiều, nhưng dữ liệu
 * trong DB sai: báo cáo SQL, sao lưu sang máy khác múi giờ, CHECK start_time < end_time quanh nửa đêm UTC).
 */
@IntegrationTest
@Transactional
class TemporalStorageIT {

    @Autowired InterviewSessionRepository sessions;
    @Autowired TrackRepository tracks;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private TimeZone original;

    @BeforeEach
    void vietnamTimeZone() {
        // Máy chủ lab và máy dev chạy giờ Việt Nam; ép để test tái hiện được cả trên CI chạy UTC
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void localDateAndTimeAreStoredVerbatim() {
        InterviewSession s = new InterviewSession();
        s.setTrack(tracks.findByCode("SE").orElseThrow());
        s.setSessionDate(LocalDate.of(2026, 11, 20));
        s.setStartTime(LocalTime.of(10, 0));
        s.setEndTime(LocalTime.of(12, 30));
        sessions.saveAndFlush(s);

        Map<String, Object> raw = jdbc.queryForMap(
                "SELECT CONVERT(varchar(10), session_date, 23) AS d, CONVERT(varchar(5), start_time, 108) AS st, "
                        + "CONVERT(varchar(5), end_time, 108) AS et FROM interview_sessions WHERE id = ?", s.getId());
        assertThat(raw).containsEntry("d", "2026-11-20").containsEntry("st", "10:00").containsEntry("et", "12:30");

        // Ghi thẳng bằng SQL (nhập liệu, công cụ ngoài) thì đọc qua JPA cũng ra đúng giá trị đó
        jdbc.update("UPDATE interview_sessions SET start_time = '16:00', end_time = '18:30' WHERE id = ?", s.getId());
        em.clear();
        InterviewSession reloaded = sessions.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStartTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(reloaded.getEndTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(reloaded.getSessionDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void instantsAreStoredAsUtc() {
        InterviewSession s = new InterviewSession();
        s.setTrack(tracks.findByCode("SE").orElseThrow());
        s.setSessionDate(LocalDate.of(2026, 11, 20));
        s.setStartTime(LocalTime.of(10, 0));
        s.setEndTime(LocalTime.of(11, 0));
        sessions.saveAndFlush(s);

        // created_at do ứng dụng ghi (Instant) phải là giờ UTC, khớp với DEFAULT SYSUTCDATETIME() của DB
        Long diffMinutes = jdbc.queryForObject(
                "SELECT ABS(DATEDIFF(minute, created_at, SYSUTCDATETIME())) FROM interview_sessions WHERE id = ?",
                Long.class, s.getId());
        assertThat(diffMinutes).isLessThan(5);
    }
}
