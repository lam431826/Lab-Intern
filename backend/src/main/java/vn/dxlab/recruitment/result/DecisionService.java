package vn.dxlab.recruitment.result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.result.dto.DecisionDto;
import vn.dxlab.recruitment.result.dto.DecisionRequest;
import vn.dxlab.recruitment.result.dto.OfferRequest;
import vn.dxlab.recruitment.result.dto.ResultRow;
import vn.dxlab.recruitment.result.dto.TrackResultDto;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUserRepository;

import java.time.Instant;
import java.util.Objects;

/**
 * Kết luận, phê duyệt và offer. Người tổ chức hoặc Giảng viên chốt kết luận; chỉ Giảng viên phê duyệt.
 * Đã phê duyệt thì khóa (cả kết luận lẫn phiếu chấm) cho tới khi Giảng viên mở lại.
 */
@Service
@Transactional
public class DecisionService {

    private static final String HISTORY_TYPE = "CANDIDATE";

    private final DecisionRepository decisions;
    private final CandidateRepository candidates;
    private final TrackRepository tracks;
    private final AppUserRepository users;
    private final ResultService results;
    private final CurrentUser currentUser;
    private final StatusHistoryService history;

    public DecisionService(DecisionRepository decisions, CandidateRepository candidates, TrackRepository tracks,
                           AppUserRepository users, ResultService results, CurrentUser currentUser,
                           StatusHistoryService history) {
        this.decisions = decisions;
        this.candidates = candidates;
        this.tracks = tracks;
        this.users = users;
        this.results = results;
        this.currentUser = currentUser;
        this.history = history;
    }

    public DecisionDto decide(Long candidateId, DecisionRequest req) {
        Candidate c = candidate(candidateId);
        Decision d = decisions.findByCandidateId(candidateId).orElse(null);
        if (d != null) {
            checkVersion(d, req.version());
            if (d.isApproved()) {
                throw BusinessException.conflict("DECISION_LOCKED",
                        "Kết luận đã được Giảng viên phê duyệt. Cần Giảng viên mở lại trước khi sửa.");
            }
        }
        ResultRow row = row(c);
        FinalStatus status = req.finalStatus();
        Track transferTo = null;

        if (status == FinalStatus.HIRED || status == FinalStatus.WAITLIST || status == FinalStatus.TRANSFER) {
            if (!row.scoresComplete()) {
                throw new BusinessException("SCORES_INCOMPLETE", "Ứng viên chưa phỏng vấn xong hoặc hội đồng chưa nộp đủ phiếu ("
                        + row.submittedSheets() + "/" + row.panelSize() + ")");
            }
        }
        if (status == FinalStatus.TRANSFER) {
            if (req.transferTrackId() == null || req.transferTrackId().equals(c.getTrack().getId())) {
                throw new BusinessException("INVALID_TRANSFER", "Chọn ngạch chuyển sang, khác ngạch hiện tại");
            }
            transferTo = tracks.findById(req.transferTrackId())
                    .orElseThrow(() -> new NotFoundException("ngạch", req.transferTrackId()));
        }
        if (status != FinalStatus.PENDING && status != row.suggestion() && req.note() == null) {
            throw new BusinessException("DECISION_NOTE_REQUIRED",
                    "Kết luận khác gợi ý của hệ thống (" + (row.suggestion() == null ? "chưa có" : row.suggestion())
                            + ") nên phải ghi lý do");
        }
        boolean takesSeatNow = d != null && d.getOfferStatus() != OfferStatus.DECLINED && seatTrack(d) != null;
        Track seat = status == FinalStatus.HIRED ? c.getTrack() : transferTo;
        if (seat != null) {
            long taken = results.seatsTaken(seat);
            // Không tính chỗ của chính ứng viên này nếu đang giữ chỗ ở cùng ngạch
            if (takesSeatNow && Objects.equals(seatTrack(d).getId(), seat.getId())) {
                taken--;
            }
            if (taken >= seat.getQuota()) {
                throw BusinessException.conflict("QUOTA_FULL", "Ngạch " + seat.getName() + " đã đủ chỉ tiêu "
                        + seat.getQuota() + ". Chọn Dự bị, hoặc tăng chỉ tiêu nếu lab đồng ý.");
            }
        }

        if (d == null) {
            d = new Decision();
            d.setCandidate(c);
        }
        history.record(HISTORY_TYPE, candidateId, "decision", d.getId() == null ? null : d.getFinalStatus(), status, req.note());
        d.setFinalStatus(status);
        d.setTransferTrack(transferTo);
        d.setNote(req.note());
        d.setOfferStatus(OfferStatus.NONE);
        d.setEmailStatus(EmailStatus.NOT_SENT);
        decisions.saveAndFlush(d);
        return ResultService.toDto(d);
    }

    public DecisionDto approve(Long candidateId, int version) {
        Decision d = decision(candidateId);
        checkVersion(d, version);
        doApprove(d);
        decisions.saveAndFlush(d);
        return ResultService.toDto(d);
    }

    /** Phê duyệt mọi kết luận đã chốt mà chưa duyệt của ngạch. */
    public TrackResultDto approveTrack(Long trackId) {
        Track t = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("ngạch", trackId));
        decisions.findByCandidateTrackId(trackId).stream()
                .filter(d -> !d.isApproved() && d.getFinalStatus() != FinalStatus.PENDING)
                .forEach(this::doApprove);
        decisions.flush();
        return results.compute(t);
    }

    public DecisionDto reopen(Long candidateId, int version) {
        Decision d = decision(candidateId);
        checkVersion(d, version);
        if (!d.isApproved()) {
            return ResultService.toDto(d);
        }
        if (d.getEmailStatus() == EmailStatus.SENT) {
            throw BusinessException.conflict("DECISION_ALREADY_SENT",
                    "Đã gửi email kết quả cho ứng viên, không mở lại kết luận được");
        }
        history.record(HISTORY_TYPE, candidateId, "approved", true, false, "Mở lại kết luận");
        d.setApproved(false);
        d.setApprovedBy(null);
        d.setApprovedAt(null);
        decisions.saveAndFlush(d);
        return ResultService.toDto(d);
    }

    /** Gửi email kết quả và ghi nhận ứng viên nhận / từ chối offer. */
    public DecisionDto offer(Long candidateId, OfferRequest req) {
        Decision d = decision(candidateId);
        checkVersion(d, req.version());
        if (!d.isApproved() || seatTrack(d) == null) {
            throw BusinessException.conflict("OFFER_NOT_ALLOWED",
                    "Chỉ gửi offer cho ứng viên Trúng tuyển hoặc Chuyển ngạch đã được phê duyệt");
        }
        if (req.offerStatus() != OfferStatus.NONE && req.emailStatus() != EmailStatus.SENT) {
            throw new BusinessException("EMAIL_NOT_SENT", "Phải gửi email kết quả trước khi ghi nhận phản hồi offer");
        }
        history.record(HISTORY_TYPE, candidateId, "emailStatus", d.getEmailStatus(), req.emailStatus(), null);
        history.record(HISTORY_TYPE, candidateId, "offerStatus", d.getOfferStatus(), req.offerStatus(), null);
        d.setEmailStatus(req.emailStatus());
        d.setOfferStatus(req.offerStatus());
        decisions.saveAndFlush(d);
        return ResultService.toDto(d);
    }

    /** Gọi dự bị: chỉ khi ngạch còn chỗ và đúng người dự bị có thứ hạng cao nhất. */
    public DecisionDto promote(Long candidateId, int version) {
        Decision d = decision(candidateId);
        checkVersion(d, version);
        if (d.getFinalStatus() != FinalStatus.WAITLIST || !d.isApproved()) {
            throw BusinessException.conflict("NOT_WAITLISTED", "Ứng viên không nằm trong danh sách dự bị đã phê duyệt");
        }
        TrackResultDto table = results.compute(d.getCandidate().getTrack());
        if (table.seatsLeft() == 0) {
            throw BusinessException.conflict("QUOTA_FULL", "Ngạch chưa có chỗ trống để gọi dự bị");
        }
        if (!candidateId.equals(table.nextWaitlistCandidateId())) {
            throw BusinessException.conflict("WAITLIST_ORDER", "Phải gọi dự bị theo thứ hạng; người kế tiếp là ứng viên "
                    + table.nextWaitlistCandidateId());
        }
        history.record(HISTORY_TYPE, candidateId, "decision", FinalStatus.WAITLIST, FinalStatus.HIRED, "Gọi dự bị");
        d.setFinalStatus(FinalStatus.HIRED);
        d.setOfferStatus(OfferStatus.NONE);
        d.setEmailStatus(EmailStatus.NOT_SENT);
        decisions.saveAndFlush(d);
        return ResultService.toDto(d);
    }

    private void doApprove(Decision d) {
        if (d.getFinalStatus() == FinalStatus.PENDING) {
            throw new BusinessException("NOTHING_TO_APPROVE", "Chưa có kết luận để phê duyệt");
        }
        if (d.isApproved()) {
            return;
        }
        history.record(HISTORY_TYPE, d.getCandidate().getId(), "approved", false, true, null);
        d.setApproved(true);
        d.setApprovedBy(users.getReferenceById(currentUser.id()));
        d.setApprovedAt(Instant.now());
    }

    /** Ngạch mà kết luận này giữ chỗ; {@code null} nếu không giữ chỗ. */
    private static Track seatTrack(Decision d) {
        return switch (d.getFinalStatus()) {
            case HIRED -> d.getCandidate().getTrack();
            case TRANSFER -> d.getTransferTrack();
            default -> null;
        };
    }

    private ResultRow row(Candidate c) {
        return results.compute(c.getTrack()).rows().stream()
                .filter(r -> r.candidateId().equals(c.getId())).findFirst()
                .orElseThrow(() -> new BusinessException("NOT_INTERVIEWABLE", "Hồ sơ chưa Đạt sàng lọc CV"));
    }

    private static void checkVersion(Decision d, Integer version) {
        if (version == null || version != d.getVersion()) {
            throw BusinessException.conflict("STALE_DATA", "Kết luận vừa được người khác cập nhật. Tải lại trang rồi thử lại.");
        }
    }

    private Decision decision(Long candidateId) {
        return decisions.findByCandidateId(candidateId)
                .orElseThrow(() -> new NotFoundException("kết luận của hồ sơ", candidateId));
    }

    private Candidate candidate(Long id) {
        return candidates.findById(id).orElseThrow(() -> new NotFoundException("hồ sơ", id));
    }
}
