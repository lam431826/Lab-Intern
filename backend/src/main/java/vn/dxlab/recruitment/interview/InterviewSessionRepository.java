package vn.dxlab.recruitment.interview;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findBySessionDateOrderByStartTimeAsc(LocalDate date);

    List<InterviewSession> findByTrackIdOrderBySessionDateAscStartTimeAsc(Long trackId);

    List<InterviewSession> findAllByOrderBySessionDateAscStartTimeAsc();

    /** Các ca mà người dùng có tên trong hội đồng. */
    @Query("""
            select distinct s from InterviewSession s join s.panel p
            where p.user.id = :userId
            order by s.sessionDate, s.startTime
            """)
    List<InterviewSession> findWherePanelMember(Long userId);

    /**
     * Khóa dòng ca khi thêm/dời slot, để hai người xếp lịch cùng lúc không đặt trùng giờ
     * (DB chỉ chặn được "mỗi ứng viên một slot", không chặn được hai slot chồng giờ).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id")
    Optional<InterviewSession> lockById(Long id);
}
