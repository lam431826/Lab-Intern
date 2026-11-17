package vn.dxlab.recruitment.candidate.dto;
import org.jspecify.annotations.NullMarked;

/** Bộ đếm sàng lọc theo ngạch (thay ô Tổng CV / Đạt / Loại / Chờ duyệt của tab Sàng lọc CV). */
@NullMarked
public record TrackScreeningSummary(Long trackId, String trackCode, String trackName,
                                    long total, long pending, long passed, long rejected, long overdue) {
}
