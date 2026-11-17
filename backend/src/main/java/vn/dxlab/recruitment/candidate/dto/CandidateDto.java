package vn.dxlab.recruitment.candidate.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.candidate.ScreeningRules;
import vn.dxlab.recruitment.candidate.ScreeningStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param flags       cờ lọc nhanh (gợi ý cho Lead, không tự loại)
 * @param cvAverage   trung bình 5 mục chấm CV, null nếu chưa chấm
 * @param slaDeadline hạn duyệt CV
 * @param overdue     còn chờ duyệt và đã quá hạn
 * @param scheduled   đã có lịch phỏng vấn đang hiệu lực
 * @param version     gửi kèm khi sàng lọc để phát hiện hai người sửa cùng lúc
 */
@NullMarked
public record CandidateDto(
        Long id, String studentCode, String fullName, @Nullable String email, @Nullable String phone, @Nullable String major,
        Long trackId, String trackCode, String trackName,
        @Nullable String cvLink, @Nullable String portfolioLink,
        ScreeningStatus screeningStatus, @Nullable String screeningReason,
        @Nullable Integer cvPersonalInfo, @Nullable Integer cvExperience, @Nullable Integer cvSkills,
        @Nullable Integer cvProjects, @Nullable Integer cvCertificates,
        @Nullable BigDecimal cvAverage,
        List<ScreeningRules.Flag> flags,
        Instant assignedAt, Instant slaDeadline, boolean overdue,
        @Nullable String screenedByName, @Nullable Instant screenedAt,
        boolean scheduled,
        int version) {
}
