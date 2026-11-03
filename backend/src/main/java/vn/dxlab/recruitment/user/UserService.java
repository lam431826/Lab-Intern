package vn.dxlab.recruitment.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.clerk.ClerkInvitationClient;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.security.UserIdentityService;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.dto.CreateUserRequest;
import vn.dxlab.recruitment.user.dto.UpdateUserRequest;
import vn.dxlab.recruitment.user.dto.UserDto;

import java.util.List;
import java.util.Locale;

/**
 * Người tổ chức tạo tài khoản (email + vai trò) rồi hệ thống gửi lời mời Clerk.
 * Người được mời đăng ký trên Clerk bằng đúng email đó, lần đăng nhập đầu sẽ tự liên kết.
 */
@Service
@Transactional
public class UserService {

    static final String HISTORY_TYPE = "USER";

    private final AppUserRepository users;
    private final TrackRepository tracks;
    private final ClerkInvitationClient invitations;
    private final UserIdentityService identities;
    private final StatusHistoryService history;
    private final CurrentUser currentUser;
    private final String frontendUrl;

    public UserService(AppUserRepository users, TrackRepository tracks, ClerkInvitationClient invitations,
                       UserIdentityService identities, StatusHistoryService history, CurrentUser currentUser,
                       @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.tracks = tracks;
        this.invitations = invitations;
        this.identities = identities;
        this.history = history;
        this.currentUser = currentUser;
        this.frontendUrl = frontendUrl;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserDto::from).toList();
    }

    /** Gửi lời mời thất bại thì không tạo tài khoản (rollback), để Người tổ chức thử lại từ đầu. */
    public UserDto create(CreateUserRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw BusinessException.conflict("USER_EMAIL_EXISTS", "Email " + email + " đã có tài khoản");
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        users.saveAndFlush(u);
        history.record(HISTORY_TYPE, u.getId(), "role", null, u.getRole(), "Tạo tài khoản");
        if (req.shouldSendInvite()) {
            invitations.invite(email, frontendUrl);
        }
        return UserDto.from(u);
    }

    public UserDto update(Long id, UpdateUserRequest req) {
        AppUser u = users.findById(id).orElseThrow(() -> new NotFoundException("tài khoản", id));
        boolean roleChanged = u.getRole() != req.role();
        boolean deactivating = u.isActive() && !req.active();

        if (id.equals(currentUser.id()) && (roleChanged || deactivating)) {
            throw new BusinessException("CANNOT_CHANGE_SELF",
                    "Không thể tự đổi vai trò hoặc tự khóa tài khoản của mình. Nhờ một Người tổ chức khác thực hiện.");
        }
        if (u.getRole() == Role.ORGANIZER && u.isActive() && (req.role() != Role.ORGANIZER || !req.active())
                && users.countByRoleAndActiveTrue(Role.ORGANIZER) <= 1) {
            throw new BusinessException("LAST_ORGANIZER", "Phải còn ít nhất một Người tổ chức đang hoạt động.");
        }
        boolean canStillLead = req.active() && (req.role() == Role.LEAD || req.role() == Role.ORGANIZER);
        if (!canStillLead && !tracks.findByLeadId(id).isEmpty()) {
            throw new BusinessException("USER_IS_TRACK_LEAD",
                    "Tài khoản đang là Lead của một ngạch. Đổi Lead của ngạch đó trước.");
        }

        history.record(HISTORY_TYPE, id, "role", u.getRole(), req.role(), null);
        history.record(HISTORY_TYPE, id, "active", u.isActive(), req.active(), null);
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setActive(req.active());
        identities.evictAll();
        return UserDto.from(u);
    }

    public void resendInvite(Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> new NotFoundException("tài khoản", id));
        if (u.getClerkUserId() != null) {
            throw new BusinessException("ALREADY_LINKED", "Tài khoản đã đăng nhập Clerk, không cần mời lại.");
        }
        if (!u.isActive()) {
            throw new BusinessException("USER_INACTIVE", "Tài khoản đang bị khóa.");
        }
        invitations.invite(u.getEmail(), frontendUrl);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
