package vn.dxlab.recruitment.scoring.dto;

import org.jspecify.annotations.NullMarked;
import vn.dxlab.recruitment.interview.Attendance;

import java.util.List;

/** Tiến độ chấm của hội đồng trong một ca. */
@NullMarked
public record SessionProgressDto(Long sessionId, int panelSize, List<CandidateProgress> candidates) {

    /** {@code pending}: tên thành viên hội đồng chưa nộp phiếu. */
    public record CandidateProgress(Long candidateId, String studentCode, String candidateName, String timeLabel,
                                    Attendance attendance, int submitted, int drafts, List<String> pending) {
    }
}
