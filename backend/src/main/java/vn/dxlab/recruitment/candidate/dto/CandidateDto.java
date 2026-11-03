package vn.dxlab.recruitment.candidate.dto;

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
public record CandidateDto(
        Long id, String studentCode, String fullName, String email, String phone, String major,
        Long trackId, String trackCode, String trackName,
        String cvLink, String portfolioLink,
        ScreeningStatus screeningStatus, String screeningReason,
        Integer cvPersonalInfo, Integer cvExperience, Integer cvSkills, Integer cvProjects, Integer cvCertificates,
        BigDecimal cvAverage,
        List<ScreeningRules.Flag> flags,
        Instant assignedAt, Instant slaDeadline, boolean overdue,
        String screenedByName, Instant screenedAt,
        boolean scheduled,
        int version) {
}
