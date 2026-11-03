package vn.dxlab.recruitment.candidate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.HistoryEntryDto;
import vn.dxlab.recruitment.audit.StatusHistory;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.candidate.dto.CandidateDto;
import vn.dxlab.recruitment.candidate.dto.CandidateRequest;
import vn.dxlab.recruitment.candidate.dto.TrackScreeningSummary;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.security.AppUserPrincipal;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.settings.SettingsService;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hồ sơ ứng viên (Phân hệ 1). Giảng viên và Người tổ chức thấy mọi ngạch; Lead chỉ thấy ngạch mình phụ trách.
 */
@Service
@Transactional
public class CandidateService {

    static final String HISTORY_TYPE = "CANDIDATE";

    private final CandidateRepository candidates;
    private final TrackRepository tracks;
    private final AppUserRepository users;
    private final InterviewSlotRepository slots;
    private final ScoreRepository scores;
    private final SettingsService settings;
    private final StatusHistoryService history;
    private final CurrentUser currentUser;

    public CandidateService(CandidateRepository candidates, TrackRepository tracks, AppUserRepository users,
                            InterviewSlotRepository slots, ScoreRepository scores, SettingsService settings,
                            StatusHistoryService history, CurrentUser currentUser) {
        this.candidates = candidates;
        this.tracks = tracks;
        this.users = users;
        this.slots = slots;
        this.scores = scores;
        this.settings = settings;
        this.history = history;
        this.currentUser = currentUser;
    }

    public record Filter(Long trackId, ScreeningStatus status, String q, boolean overdueOnly) {
    }

    @Transactional(readOnly = true)
    public List<CandidateDto> list(Filter filter) {
        Set<Long> visibleTracks = visibleTrackIds(filter.trackId());
        if (visibleTracks.isEmpty()) {
            return List.of();
        }
        Specification<Candidate> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(root.get("track").get("id").in(visibleTracks));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("screeningStatus"), filter.status()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                // Collation CI_AI: "nguyen" khớp "Nguyễn"
                String like = "%" + filter.q().trim() + "%";
                predicates.add(cb.or(cb.like(root.get("fullName"), like),
                        cb.like(root.get("studentCode"), like),
                        cb.like(root.get("email"), like)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Mapper mapper = mapper();
        return candidates.findAll(spec).stream()
                .sorted(Comparator.comparing((Candidate c) -> c.getTrack().getSortOrder()).thenComparing(Candidate::getFullName))
                .map(mapper::toDto)
                .filter(dto -> !filter.overdueOnly() || dto.overdue())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrackScreeningSummary> summary() {
        Set<Long> visibleTracks = visibleTrackIds(null);
        Mapper mapper = mapper();
        Map<Long, List<CandidateDto>> byTrack = candidates.findAll().stream()
                .filter(c -> visibleTracks.contains(c.getTrack().getId()))
                .map(mapper::toDto)
                .collect(Collectors.groupingBy(CandidateDto::trackId));
        return tracks.findAllByOrderBySortOrderAsc().stream()
                .filter(t -> visibleTracks.contains(t.getId()))
                .map(t -> {
                    List<CandidateDto> list = byTrack.getOrDefault(t.getId(), List.of());
                    return new TrackScreeningSummary(t.getId(), t.getCode(), t.getName(), list.size(),
                            count(list, ScreeningStatus.PENDING), count(list, ScreeningStatus.PASSED),
                            count(list, ScreeningStatus.REJECTED), list.stream().filter(CandidateDto::overdue).count());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateDto get(Long id) {
        return mapper().toDto(find(id));
    }

    public CandidateDto create(CandidateRequest req) {
        if (candidates.findByStudentCode(req.studentCode()).isPresent()) {
            throw BusinessException.conflict("CANDIDATE_CODE_EXISTS", "Mã SV " + req.studentCode() + " đã có hồ sơ");
        }
        Candidate c = new Candidate();
        applyInfo(c, req, track(req.trackId()), false);
        candidates.saveAndFlush(c);
        history.record(HISTORY_TYPE, c.getId(), "screeningStatus", null, c.getScreeningStatus(), "Tạo hồ sơ");
        return mapper().toDto(c);
    }

    public CandidateDto update(Long id, CandidateRequest req) {
        Candidate c = find(id);
        candidates.findByStudentCode(req.studentCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BusinessException.conflict("CANDIDATE_CODE_EXISTS", "Mã SV " + req.studentCode() + " đã có hồ sơ");
                });
        applyInfo(c, req, track(req.trackId()), slots.existsByCandidateIdAndActiveTrue(id));
        candidates.saveAndFlush(c);
        return mapper().toDto(c);
    }

    public void delete(Long id) {
        Candidate c = find(id);
        if (slots.existsByCandidateIdAndActiveTrue(id) || scores.existsByCandidateId(id)) {
            throw BusinessException.conflict("CANDIDATE_IN_USE",
                    "Hồ sơ đã được xếp lịch hoặc chấm điểm, không xóa được. Hãy đặt trạng thái Loại kèm lý do.");
        }
        candidates.delete(c);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntryDto> history(Long id) {
        find(id);
        List<StatusHistory> entries = history.of(HISTORY_TYPE, id);
        Map<Long, String> names = users.findAllById(entries.stream().map(StatusHistory::getChangedBy)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return entries.stream()
                .map(h -> new HistoryEntryDto(h.getField(), h.getOldValue(), h.getNewValue(), h.getNote(),
                        h.getChangedBy() == null ? null : names.get(h.getChangedBy()), h.getChangedAt()))
                .toList();
    }

    /**
     * Ghi thông tin hồ sơ. Đổi ngạch chỉ được khi chưa xếp lịch, và đưa hồ sơ về Chờ duyệt để Lead ngạch mới duyệt lại.
     *
     * @return {@code true} nếu có thay đổi
     */
    boolean applyInfo(Candidate c, CandidateRequest req, Track track, boolean scheduled) {
        boolean trackChanged = c.getTrack() != null && !c.getTrack().getId().equals(track.getId());
        if (trackChanged && scheduled) {
            throw new BusinessException("CANDIDATE_SCHEDULED",
                    "Hồ sơ " + c.getStudentCode() + " đã được xếp lịch phỏng vấn, không đổi ngạch được");
        }
        boolean changed = c.getId() == null || trackChanged
                || !Objects.equals(c.getStudentCode(), req.studentCode())
                || !Objects.equals(c.getFullName(), req.fullName())
                || !Objects.equals(c.getEmail(), req.email())
                || !Objects.equals(c.getPhone(), req.phone())
                || !Objects.equals(c.getMajor(), req.major())
                || !Objects.equals(c.getCvLink(), req.cvLink())
                || !Objects.equals(c.getPortfolioLink(), req.portfolioLink());
        if (trackChanged) {
            history.record(HISTORY_TYPE, c.getId(), "track", c.getTrack().getCode(), track.getCode(),
                    "Đổi ngạch, đưa hồ sơ về Chờ duyệt");
            history.record(HISTORY_TYPE, c.getId(), "screeningStatus", c.getScreeningStatus(), ScreeningStatus.PENDING, null);
            c.setScreeningStatus(ScreeningStatus.PENDING);
            c.setScreeningReason(null);
            c.setScreenedBy(null);
            c.setScreenedAt(null);
            c.setAssignedAt(Instant.now());
        }
        c.setStudentCode(req.studentCode());
        c.setFullName(req.fullName());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setMajor(req.major());
        c.setTrack(track);
        c.setCvLink(req.cvLink());
        c.setPortfolioLink(req.portfolioLink());
        return changed;
    }

    Candidate find(Long id) {
        return candidates.findById(id).orElseThrow(() -> new NotFoundException("hồ sơ", id));
    }

    Mapper mapper() {
        return new Mapper(settings.current().getSlaHours(), slots.findCandidateIdsWithActiveSlot(), Instant.now());
    }

    private Track track(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("ngạch", id));
    }

    /** Ngạch người dùng được xem; {@code requested} khác null thì phải nằm trong số đó. */
    private Set<Long> visibleTrackIds(Long requested) {
        AppUserPrincipal me = currentUser.get();
        Set<Long> visible = switch (me.role()) {
            case ORGANIZER, LECTURER -> tracks.findAll().stream().map(Track::getId).collect(Collectors.toSet());
            case LEAD -> tracks.findByLeadId(me.id()).stream().map(Track::getId).collect(Collectors.toSet());
            case MEMBER -> Set.of();
        };
        if (requested == null) {
            return visible;
        }
        if (!visible.contains(requested)) {
            throw new AccessDeniedException("Không có quyền xem hồ sơ của ngạch này");
        }
        return Set.of(requested);
    }

    private static long count(List<CandidateDto> list, ScreeningStatus status) {
        return list.stream().filter(d -> d.screeningStatus() == status).count();
    }

    /** Chuyển entity sang DTO với các giá trị tính sẵn (cờ, SLA, đã xếp lịch). */
    record Mapper(int slaHours, Set<Long> scheduledIds, Instant now) {
        CandidateDto toDto(Candidate c) {
            Track t = c.getTrack();
            return new CandidateDto(c.getId(), c.getStudentCode(), c.getFullName(), c.getEmail(), c.getPhone(), c.getMajor(),
                    t.getId(), t.getCode(), t.getName(),
                    c.getCvLink(), c.getPortfolioLink(),
                    c.getScreeningStatus(), c.getScreeningReason(),
                    c.getCvPersonalInfo(), c.getCvExperience(), c.getCvSkills(), c.getCvProjects(), c.getCvCertificates(),
                    ScreeningRules.cvAverage(c.getCvPersonalInfo(), c.getCvExperience(), c.getCvSkills(),
                            c.getCvProjects(), c.getCvCertificates()),
                    ScreeningRules.flags(c.getCvLink(), c.getPortfolioLink(), t.isRequiresPortfolio()),
                    c.getAssignedAt(), ScreeningRules.slaDeadline(c.getAssignedAt(), slaHours),
                    ScreeningRules.overdue(c.getScreeningStatus(), c.getAssignedAt(), slaHours, now),
                    c.getScreenedBy() == null ? null : c.getScreenedBy().getFullName(), c.getScreenedAt(),
                    scheduledIds.contains(c.getId()),
                    c.getVersion());
        }
    }
}
