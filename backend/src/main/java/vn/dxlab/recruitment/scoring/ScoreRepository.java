package vn.dxlab.recruitment.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    List<Score> findByCandidateId(Long candidateId);

    List<Score> findByCandidateIdAndEvaluatorId(Long candidateId, Long evaluatorId);

    boolean existsByCandidateId(Long candidateId);

    boolean existsByCriterionId(Long criterionId);

    boolean existsByEvaluatorIdAndCandidateIdIn(Long evaluatorId, java.util.Collection<Long> candidateIds);
}
