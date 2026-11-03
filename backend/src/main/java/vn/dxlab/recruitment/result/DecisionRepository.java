package vn.dxlab.recruitment.result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    Optional<Decision> findByCandidateId(Long candidateId);

    List<Decision> findByCandidateTrackId(Long trackId);

    long countByCandidateTrackIdAndFinalStatus(Long trackId, FinalStatus status);
}
