package vn.dxlab.recruitment.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecruitmentSubmissionFileRepository extends JpaRepository<RecruitmentSubmissionFile, Long> {
    Optional<RecruitmentSubmissionFile> findByItemId(Long itemId);
    void deleteByItemId(Long itemId);
}
