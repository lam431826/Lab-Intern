package vn.dxlab.recruitment.track;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.track.dto.TrackDto;
import vn.dxlab.recruitment.track.dto.UpdateTrackRequest;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.AppUserRepository;
import vn.dxlab.recruitment.user.Role;

import java.util.List;

@Service
@Transactional
public class TrackService {

    private final TrackRepository tracks;
    private final AppUserRepository users;
    private final StatusHistoryService history;

    public TrackService(TrackRepository tracks, AppUserRepository users, StatusHistoryService history) {
        this.tracks = tracks;
        this.users = users;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<TrackDto> list() {
        return tracks.findAllByOrderBySortOrderAsc().stream().map(TrackDto::from).toList();
    }

    public TrackDto update(Long id, UpdateTrackRequest req) {
        Track t = tracks.findById(id).orElseThrow(() -> new NotFoundException("ngạch", id));
        if (req.slotMin() > req.slotMax()) {
            throw new BusinessException("INVALID_SLOT_RANGE", "Số slot tối thiểu không được lớn hơn số slot tối đa");
        }
        AppUser lead = null;
        if (req.leadUserId() != null) {
            lead = users.findById(req.leadUserId()).orElseThrow(() -> new NotFoundException("tài khoản", req.leadUserId()));
            // Người tổ chức có thể kiêm Lead (Kế hoạch: Ban AI do Người tổ chức kiêm Lead)
            if (!lead.isActive() || (lead.getRole() != Role.LEAD && lead.getRole() != Role.ORGANIZER)) {
                throw new BusinessException("INVALID_TRACK_LEAD",
                        "Lead của ngạch phải là tài khoản đang hoạt động với vai trò Lead hoặc Người tổ chức");
            }
        }
        history.record("TRACK", id, "lead", t.getLead() == null ? null : t.getLead().getId(),
                lead == null ? null : lead.getId(), null);
        history.record("TRACK", id, "quota", t.getQuota(), req.quota(), null);

        t.setName(req.name().trim());
        t.setQuota(req.quota());
        t.setSlotMin(req.slotMin());
        t.setSlotMax(req.slotMax());
        t.setRequiresPortfolio(req.requiresPortfolio());
        t.setLead(lead);
        return TrackDto.from(t);
    }
}
