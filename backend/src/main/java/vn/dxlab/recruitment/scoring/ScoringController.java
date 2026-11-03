package vn.dxlab.recruitment.scoring;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.dxlab.recruitment.scoring.dto.CandidateScoresDto;
import vn.dxlab.recruitment.scoring.dto.ScoreSheetDto;
import vn.dxlab.recruitment.scoring.dto.ScoreSheetRequest;
import vn.dxlab.recruitment.scoring.dto.ScoringQueueItem;
import vn.dxlab.recruitment.scoring.dto.SessionProgressDto;

import java.util.List;

/** Chấm điểm (Phân hệ 4). Quyền gắn với hội đồng của ca, không suy ra từ vai trò. */
@RestController
public class ScoringController {

    private final ScoringService scoring;

    public ScoringController(ScoringService scoring) {
        this.scoring = scoring;
    }

    @GetMapping("/api/me/scoring")
    public List<ScoringQueueItem> myQueue() {
        return scoring.myQueue();
    }

    @GetMapping("/api/candidates/{id}/scoresheet")
    @PreAuthorize("@authz.canScore(#id)")
    public ScoreSheetDto mySheet(@PathVariable Long id) {
        return scoring.mySheet(id);
    }

    @PutMapping("/api/candidates/{id}/scoresheet")
    @PreAuthorize("@authz.canScore(#id)")
    public ScoreSheetDto saveMySheet(@PathVariable Long id, @Valid @RequestBody ScoreSheetRequest req) {
        return scoring.saveMySheet(id, req);
    }

    @GetMapping("/api/candidates/{id}/scores")
    @PreAuthorize("@authz.canSeeCandidate(#id)")
    public CandidateScoresDto candidateScores(@PathVariable Long id) {
        return scoring.candidateScores(id);
    }

    @GetMapping("/api/sessions/{id}/progress")
    @PreAuthorize("@authz.canSeeSession(#id)")
    public SessionProgressDto progress(@PathVariable Long id) {
        return scoring.progress(id);
    }
}
