package vn.dxlab.recruitment.track;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.common.text.VietnameseText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nhận diện ngạch từ chữ người dùng gõ trong file Excel: theo mã ("GAME_DEV"), theo tên
 * ("Game Developer") hoặc theo cách gọi trong các file cũ ("Graphic Designers", "SE Intern"...).
 */
@Component
public class TrackResolver {

    /** Cách gọi lấy từ các tab của ĐÁNH GIÁ ỨNG VIÊN.xlsx và NHẬN XÉT ỨNG VIÊN.xlsx. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ai intern", "AI"),
            Map.entry("graphic", "GRAPHIC"),
            Map.entry("graphic designer", "GRAPHIC"),
            Map.entry("graphic designers", "GRAPHIC"),
            Map.entry("designer", "GRAPHIC"),
            Map.entry("thiet ke do hoa", "GRAPHIC"),
            Map.entry("software engineer", "SE"),
            Map.entry("se intern", "SE"),
            Map.entry("game dev", "GAME_DEV"),
            Map.entry("game developer", "GAME_DEV"),
            Map.entry("game design", "GAME_DESIGN"),
            Map.entry("game designer", "GAME_DESIGN"),
            Map.entry("ia intern", "IA"),
            Map.entry("information assurance", "IA"),
            Map.entry("an toan thong tin", "IA"));

    private final TrackRepository tracks;

    public TrackResolver(TrackRepository tracks) {
        this.tracks = tracks;
    }

    /** Bảng tra dựng một lần cho mỗi lần nhập file. */
    @Transactional(readOnly = true)
    public Lookup lookup() {
        List<Track> all = tracks.findAll();
        Map<String, Track> byKey = new HashMap<>();
        Map<String, Track> byCode = new HashMap<>();
        for (Track t : all) {
            byCode.put(t.getCode(), t);
            byKey.put(VietnameseText.normalize(t.getCode()), t);
            byKey.put(VietnameseText.normalize(t.getName()), t);
        }
        ALIASES.forEach((alias, code) -> {
            Track t = byCode.get(code);
            if (t != null) {
                byKey.putIfAbsent(alias, t);
            }
        });
        return new Lookup(byKey);
    }

    public record Lookup(Map<String, Track> byKey) {
        public Optional<Track> find(String text) {
            return Optional.ofNullable(byKey.get(VietnameseText.normalize(text)));
        }
    }
}
