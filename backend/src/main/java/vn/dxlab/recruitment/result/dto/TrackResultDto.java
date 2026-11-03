package vn.dxlab.recruitment.result.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng kết quả một ngạch.
 *
 * @param seatsTaken           Trúng tuyển của ngạch + Chuyển vào ngạch, trừ người đã từ chối offer
 * @param nextWaitlistCandidateId người dự bị kế tiếp nên gọi khi còn chỗ (đã phê duyệt, thứ hạng cao nhất)
 */
public record TrackResultDto(
        Long trackId,
        String trackCode,
        String trackName,
        int quota,
        long seatsTaken,
        long seatsLeft,
        BigDecimal weightGeneral,
        BigDecimal weightProfessional,
        BigDecimal passThreshold,
        List<ResultRow> rows,
        Long nextWaitlistCandidateId) {
}
