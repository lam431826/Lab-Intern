package vn.dxlab.recruitment.result;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.candidate.ScreeningStatus;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.interview.InterviewSlot;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.result.dto.DecisionDto;
import vn.dxlab.recruitment.result.dto.ResultRow;
import vn.dxlab.recruitment.result.dto.TrackResultDto;
import vn.dxlab.recruitment.scoring.EvaluatorNote;
import vn.dxlab.recruitment.scoring.EvaluatorNoteRepository;
import vn.dxlab.recruitment.scoring.Recommendation;
import vn.dxlab.recruitment.scoring.Score;
import vn.dxlab.recruitment.scoring.ScoreCalculator;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.security.AppUserPrincipal;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.settings.ScoringSettings;
import vn.dxlab.recruitment.settings.SettingsService;
import vn.dxlab.recruitment.track.CriterionRepository;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bảng kết quả theo ngạch (Phân hệ 7): điểm tổng, xếp hạng, gợi ý theo điểm sàn và chỉ tiêu.
 * Điểm không lưu sẵn mà luôn tính lại từ phiếu đã nộp, nên đổi trọng số hay điểm sàn là bảng cập nhật ngay.
 */
@Service
@Transactional(readOnly = true)
public class ResultService {

    private final TrackRepository tracks;
    private final CandidateRepository candidates;
    private final InterviewSlotRepository slots;
    private final EvaluatorNoteRepository notes;
    private final ScoreRepository scores;
    private final CriterionRepository criteria;
    private final DecisionRepository decisions;
    private final SettingsService settings;
    private final CurrentUser currentUser;

    public ResultService(TrackRepository tracks, CandidateRepository candidates, InterviewSlotRepository slots,
                         EvaluatorNoteRepository notes, ScoreRepository scores, CriterionRepository criteria,
                         DecisionRepository decisions, SettingsService settings, CurrentUser currentUser) {
        this.tracks = tracks;
        this.candidates = candidates;
        this.slots = slots;
        this.notes = notes;
        this.scores = scores;
        this.criteria = criteria;
        this.decisions = decisions;
        this.settings = settings;
        this.currentUser = currentUser;
    }

    /** Giảng viên, Người tổ chức: mọi ngạch; Lead: ngạch mình. */
    public List<TrackResultDto> list() {
        return visibleTracks().stream().map(this::compute).toList();
    }

    public TrackResultDto track(Long trackId) {
        Track t = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("ngạch", trackId));
        if (visibleTracks().stream().noneMatch(v -> v.getId().equals(trackId))) {
            throw new AccessDeniedException("Không có quyền xem kết quả của ngạch này");
        }
        return compute(t);
    }

    /** Bảng kết quả không kiểm tra quyền, dùng nội bộ khi chốt kết luận. */
    TrackResultDto compute(Track t) {
        ScoringSettings w = settings.current();
        List<ScoreCalculator.CriterionRef> sheet = criteria.findApplicableToTrack(t.getId()).stream()
                .map(c -> new ScoreCalculator.CriterionRef(c.getId(), c.getGroup())).toList();
        Map<Long, Decision> decisionByCandidate = decisions.findByCandidateTrackId(t.getId()).stream()
                .collect(Collectors.toMap(d -> d.getCandidate().getId(), d -> d));

        List<Draft> drafts = new ArrayList<>();
        for (Candidate c : candidates.findByTrackIdAndScreeningStatus(t.getId(), ScreeningStatus.PASSED)) {
            Optional<InterviewSlot> slot = slots.findByCandidateIdAndActiveTrue(c.getId());
            String interview = slot.map(s -> switch (s.getAttendance()) {
                case PENDING -> "SCHEDULED";
                case ATTENDED -> "ATTENDED";
                case ABSENT -> "ABSENT";
            }).orElse("NOT_SCHEDULED");
            List<EvaluatorNote> submitted = notes.findByCandidateId(c.getId()).stream()
                    .filter(n -> n.getSubmittedAt() != null).toList();
            Set<Long> submittedIds = submitted.stream().map(n -> n.getEvaluator().getId()).collect(Collectors.toSet());
            Map<Long, Map<Long, Integer>> byEvaluator = new HashMap<>();
            for (Score s : scores.findByCandidateId(c.getId())) {
                if (submittedIds.contains(s.getEvaluator().getId())) {
                    byEvaluator.computeIfAbsent(s.getEvaluator().getId(), k -> new HashMap<>())
                            .put(s.getCriterion().getId(), s.getValue());
                }
            }
            ScoreCalculator.Result r = ScoreCalculator.calculate(sheet, byEvaluator.values(),
                    w.getWeightGeneral(), w.getWeightProfessional());
            drafts.add(new Draft(c, interview, submitted.size(),
                    slot.map(s -> s.getSession().getPanel().size()).orElse(0),
                    submitted.stream().filter(n -> n.getRecommendation() == Recommendation.PASS).count(),
                    submitted.stream().filter(n -> n.getRecommendation() == Recommendation.FAIL).count(),
                    r, r.meets(w.getPassThreshold()), decisionByCandidate.get(c.getId())));
        }

        // Xếp hạng: chỉ ứng viên đã phỏng vấn và có điểm tổng. Hòa điểm thì ưu tiên điểm chuyên môn, rồi điểm chung.
        List<Draft> ranked = drafts.stream()
                .filter(d -> d.interview.equals("ATTENDED") && d.result.total() != null)
                .sorted(Comparator.comparing((Draft d) -> d.result.total()).reversed()
                        .thenComparing(d -> d.result.professionalAverage(), Comparator.reverseOrder())
                        .thenComparing(d -> d.result.generalAverage(), Comparator.reverseOrder())
                        .thenComparing(d -> d.candidate.getStudentCode()))
                .toList();
        Map<Long, Integer> rank = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            rank.put(ranked.get(i).candidate.getId(), i + 1);
        }

        List<ResultRow> rows = drafts.stream()
                .sorted(Comparator.comparing((Draft d) -> rank.getOrDefault(d.candidate.getId(), Integer.MAX_VALUE))
                        .thenComparing(d -> d.candidate.getStudentCode()))
                .map(d -> {
                    Integer r = rank.get(d.candidate.getId());
                    return new ResultRow(d.candidate.getId(), d.candidate.getStudentCode(), d.candidate.getFullName(),
                            d.interview, d.submitted, d.panelSize, d.passVotes, d.failVotes,
                            d.result.generalAverage(), d.result.professionalAverage(), d.result.total(), d.meets,
                            r, suggestion(d, r, t.getQuota()), d.decision == null ? null : toDto(d.decision));
                })
                .toList();

        long taken = seatsTaken(t);
        long left = Math.max(0, t.getQuota() - taken);
        Long nextWaitlist = left == 0 ? null : rows.stream()
                .filter(row -> row.decision() != null && row.decision().approved()
                        && row.decision().finalStatus() == FinalStatus.WAITLIST)
                .findFirst().map(ResultRow::candidateId).orElse(null);
        return new TrackResultDto(t.getId(), t.getCode(), t.getName(), t.getQuota(), taken, left,
                w.getWeightGeneral(), w.getWeightProfessional(), w.getPassThreshold(), rows, nextWaitlist);
    }

    /** Chỗ đã dùng: Trúng tuyển của ngạch và Chuyển vào ngạch, trừ người đã từ chối offer. */
    long seatsTaken(Track t) {
        return decisions.findAll().stream()
                .filter(d -> d.getOfferStatus() != OfferStatus.DECLINED)
                .filter(d -> (d.getFinalStatus() == FinalStatus.HIRED && d.getCandidate().getTrack().getId().equals(t.getId()))
                        || (d.getFinalStatus() == FinalStatus.TRANSFER && d.getTransferTrack() != null
                        && d.getTransferTrack().getId().equals(t.getId())))
                .count();
    }

    List<Track> visibleTracks() {
        AppUserPrincipal me = currentUser.get();
        return switch (me.role()) {
            case ORGANIZER, LECTURER -> tracks.findAllByOrderBySortOrderAsc();
            case LEAD -> tracks.findByLeadId(me.id()).stream().sorted(Comparator.comparing(Track::getSortOrder)).toList();
            case MEMBER -> List.of();
        };
    }

    static DecisionDto toDto(Decision d) {
        return new DecisionDto(d.getId(), d.getFinalStatus(),
                d.getTransferTrack() == null ? null : d.getTransferTrack().getId(),
                d.getTransferTrack() == null ? null : d.getTransferTrack().getCode(),
                d.getNote(), d.isApproved(), d.getApprovedBy() == null ? null : d.getApprovedBy().getFullName(),
                d.getApprovedAt(), d.getEmailStatus(), d.getOfferStatus(), d.getVersion());
    }

    /**
     * Đủ điểm sàn và nằm trong chỉ tiêu: Trúng tuyển; đủ sàn nhưng ngoài chỉ tiêu: Dự bị;
     * dưới sàn hoặc vắng mặt: Loại. Chưa phỏng vấn xong thì chưa gợi ý.
     */
    private static FinalStatus suggestion(Draft d, Integer rank, int quota) {
        if (d.interview.equals("ABSENT")) {
            return FinalStatus.REJECTED;
        }
        if (rank == null) {
            return null;
        }
        if (!d.meets) {
            return FinalStatus.REJECTED;
        }
        return rank <= quota ? FinalStatus.HIRED : FinalStatus.WAITLIST;
    }

    private record Draft(Candidate candidate, String interview, int submitted, int panelSize, long passVotes,
                         long failVotes, ScoreCalculator.Result result, boolean meets, Decision decision) {
    }
}
