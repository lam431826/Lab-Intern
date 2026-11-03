package vn.dxlab.recruitment.scoring;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.interview.Attendance;
import vn.dxlab.recruitment.interview.InterviewRules;
import vn.dxlab.recruitment.interview.InterviewSession;
import vn.dxlab.recruitment.interview.InterviewSessionRepository;
import vn.dxlab.recruitment.interview.InterviewSlot;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.interview.PanelMember;
import vn.dxlab.recruitment.result.Decision;
import vn.dxlab.recruitment.result.DecisionRepository;
import vn.dxlab.recruitment.scoring.dto.CandidateScoresDto;
import vn.dxlab.recruitment.scoring.dto.CandidateScoresDto.CriterionAverage;
import vn.dxlab.recruitment.scoring.dto.CandidateScoresDto.EvaluatorSheet;
import vn.dxlab.recruitment.scoring.dto.ScoreEntry;
import vn.dxlab.recruitment.scoring.dto.ScoreSheetDto;
import vn.dxlab.recruitment.scoring.dto.ScoreSheetRequest;
import vn.dxlab.recruitment.scoring.dto.ScoringQueueItem;
import vn.dxlab.recruitment.scoring.dto.SessionProgressDto;
import vn.dxlab.recruitment.security.AuthorizationService;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.settings.ScoringSettings;
import vn.dxlab.recruitment.settings.SettingsService;
import vn.dxlab.recruitment.track.Criterion;
import vn.dxlab.recruitment.track.CriterionRepository;
import vn.dxlab.recruitment.user.AppUserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phiếu chấm (Phân hệ 4). Chỉ người có tên trong hội đồng của ca, với ứng viên đã điểm danh có mặt,
 * mới chấm được (kể cả Giảng viên). Giám khảo chấm độc lập: chưa nộp phiếu của mình thì không xem
 * được điểm của người khác. Kết luận đã được phê duyệt thì khóa phiếu.
 */
@Service
@Transactional
public class ScoringService {

    private final CandidateRepository candidates;
    private final CriterionRepository criteria;
    private final ScoreRepository scores;
    private final EvaluatorNoteRepository notes;
    private final InterviewSlotRepository slots;
    private final InterviewSessionRepository sessions;
    private final DecisionRepository decisions;
    private final AppUserRepository users;
    private final AuthorizationService authz;
    private final CurrentUser currentUser;
    private final EntityManager em;
    private final SettingsService settings;

    public ScoringService(CandidateRepository candidates, CriterionRepository criteria, ScoreRepository scores,
                          EvaluatorNoteRepository notes, InterviewSlotRepository slots, InterviewSessionRepository sessions,
                          DecisionRepository decisions, AppUserRepository users, AuthorizationService authz,
                          CurrentUser currentUser, EntityManager em, SettingsService settings) {
        this.candidates = candidates;
        this.criteria = criteria;
        this.scores = scores;
        this.notes = notes;
        this.slots = slots;
        this.sessions = sessions;
        this.decisions = decisions;
        this.users = users;
        this.authz = authz;
        this.currentUser = currentUser;
        this.em = em;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public ScoreSheetDto mySheet(Long candidateId) {
        Candidate c = candidate(candidateId);
        requireCanScore(candidateId);
        return toSheet(c, notes.findByCandidateIdAndEvaluatorId(candidateId, currentUser.id()).orElse(null));
    }

    public ScoreSheetDto saveMySheet(Long candidateId, ScoreSheetRequest req) {
        Candidate c = candidate(candidateId);
        requireCanScore(candidateId);
        lockReason(candidateId).ifPresent(reason -> {
            throw BusinessException.conflict("SCORING_LOCKED", reason);
        });
        Long me = currentUser.id();
        EvaluatorNote note = notes.findByCandidateIdAndEvaluatorId(candidateId, me).orElse(null);
        if (note != null && (req.version() == null || req.version() != note.getVersion())) {
            throw stale();
        }

        Map<Long, Criterion> applicable = applicable(c).stream()
                .collect(Collectors.toMap(Criterion::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, Integer> wanted = new HashMap<>();
        for (ScoreEntry e : req.scores()) {
            if (!applicable.containsKey(e.criterionId())) {
                throw new BusinessException("INVALID_CRITERION",
                        "Tiêu chí " + e.criterionId() + " không thuộc phiếu chấm của ngạch " + c.getTrack().getName());
            }
            if (wanted.containsKey(e.criterionId())) {
                throw new BusinessException("DUPLICATE_CRITERION", "Tiêu chí " + e.criterionId() + " bị gửi hai lần");
            }
            wanted.put(e.criterionId(), e.value());
        }

        Map<Long, Score> existing = scores.findByCandidateIdAndEvaluatorId(candidateId, me).stream()
                .collect(Collectors.toMap(s -> s.getCriterion().getId(), Function.identity()));
        Map<Long, Integer> result = new HashMap<>();
        existing.forEach((criterionId, s) -> result.put(criterionId, s.getValue()));
        wanted.forEach((criterionId, value) -> {
            if (value == null) {
                result.remove(criterionId);
            } else {
                result.put(criterionId, value);
            }
        });

        boolean submitted = req.submit() || (note != null && note.getSubmittedAt() != null);
        if (submitted) {
            List<String> missing = applicable.values().stream()
                    .filter(cr -> !result.containsKey(cr.getId())).map(Criterion::getName).toList();
            if (!missing.isEmpty() || req.recommendation() == null) {
                List<String> parts = new ArrayList<>(missing);
                if (req.recommendation() == null) {
                    parts.add("Đề xuất Đạt/Không đạt");
                }
                throw new BusinessException("INCOMPLETE_SHEET", "Phiếu nộp phải đủ: " + String.join(", ", parts));
            }
        }

        // Phiếu đã qua mọi kiểm tra: tăng version trước khi ghi. Kiểm tra và tăng trong một câu UPDATE,
        // nên hai lần lưu đồng thời cùng version không cùng lọt qua; tăng cả khi chỉ đổi điểm.
        if (note != null) {
            if (notes.bumpVersion(note.getId(), req.version()) == 0) {
                throw stale();
            }
            em.refresh(note);
        }

        // Ghi điểm
        wanted.forEach((criterionId, value) -> {
            Score s = existing.get(criterionId);
            if (value == null) {
                if (s != null) {
                    scores.delete(s);
                }
            } else if (s == null) {
                Score created = new Score();
                created.setCandidate(c);
                created.setEvaluator(users.getReferenceById(me));
                created.setCriterion(applicable.get(criterionId));
                created.setValue(value);
                scores.save(created);
            } else {
                s.setValue(value);
            }
        });

        if (note == null) {
            note = new EvaluatorNote();
            note.setCandidate(c);
            note.setEvaluator(users.getReferenceById(me));
        }
        note.setComment(req.comment());
        note.setRecommendation(req.recommendation());
        if (submitted && note.getSubmittedAt() == null) {
            note.setSubmittedAt(Instant.now());
        }
        notes.saveAndFlush(note);
        return toSheet(c, note);
    }

    /** Điểm của cả hội đồng. Thành viên hội đồng phải nộp phiếu của mình trước. */
    @Transactional(readOnly = true)
    public CandidateScoresDto candidateScores(Long candidateId) {
        Candidate c = candidate(candidateId);
        if (!authz.canSeeCandidate(candidateId)) {
            throw new AccessDeniedException("Không có quyền xem điểm của ứng viên này");
        }
        Optional<InterviewSlot> slot = slots.findByCandidateIdAndActiveTrue(candidateId);
        Long me = currentUser.id();
        boolean onPanel = slot.map(s -> authz.isPanelMember(s.getSession().getId())).orElse(false);
        boolean mineSubmitted = notes.findByCandidateIdAndEvaluatorId(candidateId, me)
                .map(n -> n.getSubmittedAt() != null).orElse(false);
        if (onPanel && !mineSubmitted) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SUBMIT_FIRST",
                    "Bạn ngồi hội đồng của ứng viên này. Hãy nộp phiếu chấm của mình trước khi xem điểm của người khác.");
        }

        List<EvaluatorNote> submittedNotes = notes.findByCandidateId(candidateId).stream()
                .filter(n -> n.getSubmittedAt() != null)
                .sorted(Comparator.comparing(EvaluatorNote::getSubmittedAt))
                .toList();
        Set<Long> submittedIds = submittedNotes.stream().map(n -> n.getEvaluator().getId()).collect(Collectors.toSet());
        List<Score> counted = scores.findByCandidateId(candidateId).stream()
                .filter(s -> submittedIds.contains(s.getEvaluator().getId()))
                .toList();

        List<EvaluatorSheet> sheets = submittedNotes.stream().map(n -> new EvaluatorSheet(
                n.getEvaluator().getId(), n.getEvaluator().getFullName(), n.getSubmittedAt(), n.getRecommendation(),
                n.getComment(),
                counted.stream().filter(s -> s.getEvaluator().getId().equals(n.getEvaluator().getId()))
                        .collect(Collectors.toMap(s -> s.getCriterion().getId(), Score::getValue)))).toList();

        List<Criterion> sheetCriteria = applicable(c);
        ScoringSettings w = settings.current();
        ScoreCalculator.Result r = ScoreCalculator.calculate(
                sheetCriteria.stream().map(cr -> new ScoreCalculator.CriterionRef(cr.getId(), cr.getGroup())).toList(),
                sheets.stream().map(EvaluatorSheet::values).toList(), w.getWeightGeneral(), w.getWeightProfessional());
        List<CriterionAverage> averages = sheetCriteria.stream()
                .map(cr -> new CriterionAverage(cr.getId(), cr.getGroup(), cr.getName(),
                        r.criterionAverages().get(cr.getId()), r.criterionCounts().getOrDefault(cr.getId(), 0)))
                .toList();

        int panelSize = slot.map(s -> s.getSession().getPanel().size()).orElse(0);
        return new CandidateScoresDto(candidateId, panelSize, submittedNotes.size(), averages,
                r.generalAverage(), r.professionalAverage(), r.total(), sheets);
    }

    /** Ai trong hội đồng đã nộp, ai còn nháp, ai chưa chấm, theo từng ứng viên của ca. */
    @Transactional(readOnly = true)
    public SessionProgressDto progress(Long sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("ca phỏng vấn", sessionId));
        List<PanelMember> panel = session.getPanel();
        List<SessionProgressDto.CandidateProgress> rows = slots.findBySessionIdAndActiveTrueOrderByStartTimeAsc(sessionId)
                .stream().map(slot -> {
                    Candidate c = slot.getCandidate();
                    Map<Long, EvaluatorNote> byEvaluator = notes.findByCandidateId(c.getId()).stream()
                            .collect(Collectors.toMap(n -> n.getEvaluator().getId(), Function.identity()));
                    int submitted = 0;
                    int drafts = 0;
                    List<String> pending = new ArrayList<>();
                    for (PanelMember p : panel) {
                        EvaluatorNote n = byEvaluator.get(p.getUser().getId());
                        if (n != null && n.getSubmittedAt() != null) {
                            submitted++;
                        } else {
                            if (n != null) {
                                drafts++;
                            }
                            pending.add(p.getUser().getFullName());
                        }
                    }
                    return new SessionProgressDto.CandidateProgress(c.getId(), c.getStudentCode(), c.getFullName(),
                            InterviewRules.label(slot.getStartTime(), slot.getEndTime()), slot.getAttendance(),
                            submitted, drafts, pending);
                }).toList();
        return new SessionProgressDto(sessionId, panel.size(), rows);
    }

    /** Ứng viên đã có mặt trong các ca mình ngồi hội đồng. */
    @Transactional(readOnly = true)
    public List<ScoringQueueItem> myQueue() {
        Long me = currentUser.id();
        Set<Long> mySessions = sessions.findWherePanelMember(me).stream()
                .map(InterviewSession::getId).collect(Collectors.toSet());
        if (mySessions.isEmpty()) {
            return List.of();
        }
        return slots.findActiveInSessions(mySessions).stream()
                .filter(s -> s.getAttendance() == Attendance.ATTENDED)
                .map(s -> {
                    Candidate c = s.getCandidate();
                    String status = notes.findByCandidateIdAndEvaluatorId(c.getId(), me)
                            .map(n -> n.getSubmittedAt() != null ? "SUBMITTED" : "DRAFT").orElse("NONE");
                    return new ScoringQueueItem(c.getId(), c.getStudentCode(), c.getFullName(), c.getTrack().getCode(),
                            s.getSession().getId(), s.getSession().getSessionDate(),
                            InterviewRules.label(s.getStartTime(), s.getEndTime()), status);
                }).toList();
    }

    private ScoreSheetDto toSheet(Candidate c, EvaluatorNote note) {
        Map<Long, Integer> mine = scores.findByCandidateIdAndEvaluatorId(c.getId(), currentUser.id()).stream()
                .collect(Collectors.toMap(s -> s.getCriterion().getId(), Score::getValue));
        List<ScoreSheetDto.Item> items = applicable(c).stream()
                .map(cr -> new ScoreSheetDto.Item(cr.getId(), cr.getGroup(), cr.getName(), cr.getDescription(),
                        cr.getRubricLow(), cr.getRubricMid(), cr.getRubricHigh(), mine.get(cr.getId())))
                .toList();
        Optional<String> locked = lockReason(c.getId());
        return new ScoreSheetDto(c.getId(), c.getStudentCode(), c.getFullName(), c.getTrack().getCode(), items,
                note == null ? null : note.getComment(), note == null ? null : note.getRecommendation(),
                note == null ? null : note.getSubmittedAt(), note == null ? null : note.getVersion(),
                locked.isEmpty(), locked.orElse(null));
    }

    private List<Criterion> applicable(Candidate c) {
        return criteria.findApplicableToTrack(c.getTrack().getId());
    }

    private Optional<String> lockReason(Long candidateId) {
        return decisions.findByCandidateId(candidateId).filter(Decision::isApproved)
                .map(d -> "Kết luận của ứng viên đã được Giảng viên phê duyệt, phiếu chấm bị khóa");
    }

    private void requireCanScore(Long candidateId) {
        if (!authz.canScore(candidateId)) {
            throw new AccessDeniedException("Chỉ thành viên hội đồng của ca, với ứng viên đã có mặt, mới chấm được");
        }
    }

    private static BusinessException stale() {
        return BusinessException.conflict("STALE_DATA",
                "Phiếu chấm vừa được lưu ở nơi khác (tab hoặc máy khác). Tải lại phiếu rồi thử lại.");
    }

    private Candidate candidate(Long id) {
        return candidates.findById(id).orElseThrow(() -> new NotFoundException("hồ sơ", id));
    }
}
