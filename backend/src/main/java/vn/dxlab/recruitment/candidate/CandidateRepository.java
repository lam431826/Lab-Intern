package vn.dxlab.recruitment.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findByStudentCode(String studentCode);

    List<Candidate> findByTrackIdAndScreeningStatus(Long trackId, ScreeningStatus status);
}
