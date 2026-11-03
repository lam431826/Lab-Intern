package vn.dxlab.recruitment.track;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.scoring.ScoreRepository;
import vn.dxlab.recruitment.security.AuthorizationService;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.track.dto.CriterionDto;
import vn.dxlab.recruitment.track.dto.CriterionRequest;
import vn.dxlab.recruitment.user.Role;

import java.util.List;

/**
 * Tiêu chí chung: chỉ Người tổ chức sửa. Tiêu chí chuyên môn: Người tổ chức hoặc Lead của ngạch.
 * Tiêu chí đã có điểm thì không xóa cứng mà ẩn đi, để kết quả cũ vẫn tính lại được.
 */
@Service
@Transactional
public class CriterionService {

    private final CriterionRepository criteria;
    private final TrackRepository tracks;
    private final ScoreRepository scores;
    private final AuthorizationService authz;
    private final CurrentUser currentUser;

    public CriterionService(CriterionRepository criteria, TrackRepository tracks, ScoreRepository scores,
                            AuthorizationService authz, CurrentUser currentUser) {
        this.criteria = criteria;
        this.tracks = tracks;
        this.scores = scores;
        this.authz = authz;
        this.currentUser = currentUser;
    }

    /** trackId có giá trị: tiêu chí dùng để chấm ngạch đó (chung + chuyên môn). Không có: tiêu chí chung. */
    @Transactional(readOnly = true)
    public List<CriterionDto> list(Long trackId) {
        List<Criterion> result = trackId == null
                ? criteria.findByGroupAndActiveTrueOrderBySortOrderAsc(CriterionGroup.GENERAL)
                : criteria.findApplicableToTrack(trackId);
        return result.stream().map(CriterionDto::from).toList();
    }

    public CriterionDto create(CriterionRequest req) {
        requireCanEdit(req.trackId());
        Criterion c = new Criterion();
        if (req.trackId() == null) {
            c.setGroup(CriterionGroup.GENERAL);
        } else {
            c.setGroup(CriterionGroup.PROFESSIONAL);
            c.setTrack(tracks.findById(req.trackId()).orElseThrow(() -> new NotFoundException("ngạch", req.trackId())));
        }
        apply(c, req);
        return CriterionDto.from(criteria.save(c));
    }

    /** Không chuyển tiêu chí sang ngạch khác: điểm đã chấm gắn với ngạch hiện tại. */
    public CriterionDto update(Long id, CriterionRequest req) {
        Criterion c = criteria.findById(id).orElseThrow(() -> new NotFoundException("tiêu chí", id));
        requireCanEdit(trackIdOf(c));
        apply(c, req);
        return CriterionDto.from(c);
    }

    public void delete(Long id) {
        Criterion c = criteria.findById(id).orElseThrow(() -> new NotFoundException("tiêu chí", id));
        requireCanEdit(trackIdOf(c));
        if (scores.existsByCriterionId(id)) {
            c.setActive(false);
        } else {
            criteria.delete(c);
        }
    }

    private void requireCanEdit(Long trackId) {
        boolean allowed = currentUser.hasRole(Role.ORGANIZER) || (trackId != null && authz.isLeadOf(trackId));
        if (!allowed) {
            throw new AccessDeniedException("Không có quyền sửa tiêu chí này");
        }
    }

    private static Long trackIdOf(Criterion c) {
        return c.getTrack() == null ? null : c.getTrack().getId();
    }

    private static void apply(Criterion c, CriterionRequest req) {
        c.setName(req.name().trim());
        c.setDescription(blankToNull(req.description()));
        c.setRubricLow(blankToNull(req.rubricLow()));
        c.setRubricMid(blankToNull(req.rubricMid()));
        c.setRubricHigh(blankToNull(req.rubricHigh()));
        c.setSortOrder(req.sortOrder());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
