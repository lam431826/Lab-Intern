package vn.dxlab.recruitment.user;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.dxlab.recruitment.security.AppUserPrincipal;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;

import java.util.List;

/** Thông tin người đang đăng nhập; frontend gọi ngay sau khi Clerk đăng nhập xong để dựng menu theo vai trò. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentUser currentUser;
    private final TrackRepository tracks;

    public MeController(CurrentUser currentUser, TrackRepository tracks) {
        this.currentUser = currentUser;
        this.tracks = tracks;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public MeDto me() {
        AppUserPrincipal me = currentUser.get();
        List<String> leadTracks = tracks.findByLeadId(me.id()).stream().map(Track::getCode).toList();
        return new MeDto(me.id(), me.email(), me.fullName(), me.role(), leadTracks);
    }

    public record MeDto(Long id, String email, String fullName, Role role, List<String> leadTrackCodes) {
    }
}
