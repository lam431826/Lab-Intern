package vn.dxlab.recruitment.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluatorNoteRepository extends JpaRepository<EvaluatorNote, Long> {

    Optional<EvaluatorNote> findByCandidateIdAndEvaluatorId(Long candidateId, Long evaluatorId);

    List<EvaluatorNote> findByCandidateId(Long candidateId);

    /**
     * Tăng version nếu phiếu vẫn ở đúng version client đã mở; trả về 0 nếu đã bị lưu ở nơi khác.
     * Kiểm tra và tăng trong một câu lệnh nên hai lần lưu đồng thời không cùng lọt qua.
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "update EvaluatorNote n set n.version = n.version + 1 where n.id = :id and n.version = :version")
    int bumpVersion(Long id, int version);
}
