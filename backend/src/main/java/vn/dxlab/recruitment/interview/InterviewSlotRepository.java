package vn.dxlab.recruitment.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, Long> {

    Optional<InterviewSlot> findByCandidateIdAndActiveTrue(Long candidateId);

    List<InterviewSlot> findBySessionIdOrderByStartTimeAsc(Long sessionId);

    List<InterviewSlot> findBySessionIdAndActiveTrueOrderByStartTimeAsc(Long sessionId);

    List<InterviewSlot> findByCandidateIdOrderByIdAsc(Long candidateId);

    Optional<InterviewSlot> findByReplacedById(Long slotId);

    boolean existsByCandidateIdAndActiveTrue(Long candidateId);

    boolean existsBySessionId(Long sessionId);

    long countBySessionTrackIdAndActiveTrue(Long trackId);

    @Query("select s.candidate.id from InterviewSlot s where s.active = true")
    Set<Long> findCandidateIdsWithActiveSlot();

    @Query("""
            select s from InterviewSlot s join fetch s.session ss join fetch s.candidate
            where s.active = true and ss.id in :sessionIds
            order by ss.sessionDate, s.startTime
            """)
    List<InterviewSlot> findActiveInSessions(Set<Long> sessionIds);
}
