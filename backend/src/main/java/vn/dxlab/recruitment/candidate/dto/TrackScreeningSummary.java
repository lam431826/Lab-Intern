package vn.dxlab.recruitment.candidate.dto;

/** Bộ đếm sàng lọc theo ngạch (thay ô Tổng CV / Đạt / Loại / Chờ duyệt của tab Sàng lọc CV). */
public record TrackScreeningSummary(Long trackId, String trackCode, String trackName,
                                    long total, long pending, long passed, long rejected, long overdue) {
}
