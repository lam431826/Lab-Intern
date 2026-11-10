package vn.dxlab.recruitment.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface RecruitmentSubmissionItemRepository extends JpaRepository<RecruitmentSubmissionItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RecruitmentSubmissionItem i join fetch i.submission where i.id = :id")
    Optional<RecruitmentSubmissionItem> lockById(Long id);
}
