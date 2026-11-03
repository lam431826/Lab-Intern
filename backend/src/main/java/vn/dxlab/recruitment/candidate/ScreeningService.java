package vn.dxlab.recruitment.candidate;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.candidate.dto.CandidateDto;
import vn.dxlab.recruitment.candidate.dto.ScreeningRequest;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.security.AuthorizationService;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.user.AppUserRepository;

import java.time.Instant;

/**
 * Lead (hoặc Người tổ chức) chấm 5 mục CV và chốt Đạt / Loại. Loại thì bắt buộc ghi lý do.
 * Hồ sơ đã xếp lịch phỏng vấn thì không đổi khỏi trạng thái Đạt; phải hủy lịch trước.
 */
@Service
@Transactional
public class ScreeningService {

    private final CandidateService candidateService;
    private final CandidateRepository candidates;
    private final InterviewSlotRepository slots;
    private final AppUserRepository users;
    private final AuthorizationService authz;
    private final CurrentUser currentUser;
    private final StatusHistoryService history;

    public ScreeningService(CandidateService candidateService, CandidateRepository candidates,
                            InterviewSlotRepository slots, AppUserRepository users, AuthorizationService authz,
                            CurrentUser currentUser, StatusHistoryService history) {
        this.candidateService = candidateService;
        this.candidates = candidates;
        this.slots = slots;
        this.users = users;
        this.authz = authz;
        this.currentUser = currentUser;
        this.history = history;
    }

    public CandidateDto screen(Long id, ScreeningRequest req) {
        Candidate c = candidateService.find(id);
        if (!authz.canScreenTrack(c.getTrack().getId())) {
            throw new AccessDeniedException("Chỉ Lead của ngạch hoặc Người tổ chức được duyệt hồ sơ này");
        }
        if (req.version() != c.getVersion()) {
            throw BusinessException.conflict("STALE_DATA",
                    "Hồ sơ vừa được người khác cập nhật. Tải lại trang rồi thử lại.");
        }
        if (req.status() == ScreeningStatus.REJECTED && req.reason() == null) {
            throw new BusinessException("REASON_REQUIRED", "Loại hồ sơ thì phải ghi lý do");
        }
        if (req.status() != ScreeningStatus.PASSED && slots.existsByCandidateIdAndActiveTrue(id)) {
            throw BusinessException.conflict("CANDIDATE_SCHEDULED",
                    "Hồ sơ đã có lịch phỏng vấn. Hủy lịch trước khi đổi kết quả sàng lọc.");
        }

        if (c.getScreeningStatus() != req.status()) {
            history.record(CandidateService.HISTORY_TYPE, id, "screeningStatus", c.getScreeningStatus(), req.status(), req.reason());
            if (req.status() == ScreeningStatus.PENDING) {
                c.setScreenedBy(null);
                c.setScreenedAt(null);
            } else {
                c.setScreenedBy(users.getReferenceById(currentUser.id()));
                c.setScreenedAt(Instant.now());
            }
        }
        c.setScreeningStatus(req.status());
        c.setScreeningReason(req.reason());
        c.setCvPersonalInfo(req.cvPersonalInfo());
        c.setCvExperience(req.cvExperience());
        c.setCvSkills(req.cvSkills());
        c.setCvProjects(req.cvProjects());
        c.setCvCertificates(req.cvCertificates());
        candidates.saveAndFlush(c);
        return candidateService.mapper().toDto(c);
    }
}
