package vn.dxlab.recruitment.submission;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface RecruitmentSubmissionRepository extends JpaRepository<RecruitmentSubmission, Long> {

    @EntityGraph(attributePaths = {"items", "items.track", "submittedBy", "replaces"})
    Optional<RecruitmentSubmission> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"items", "items.track", "submittedBy"})
    List<RecruitmentSubmission> findAllByOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecruitmentSubmission s where s.id = :id")
    Optional<RecruitmentSubmission> lockById(Long id);
}
