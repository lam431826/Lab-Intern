package vn.dxlab.recruitment.scoring.dto;

import java.time.LocalDate;

/** Một ứng viên người đang đăng nhập cần chấm. {@code sheetStatus}: NONE, DRAFT, SUBMITTED. */
public record ScoringQueueItem(Long candidateId, String studentCode, String candidateName, String trackCode,
                               Long sessionId, LocalDate sessionDate, String timeLabel, String sheetStatus) {
}
