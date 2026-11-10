package vn.dxlab.recruitment.submission.dto;

import vn.dxlab.recruitment.submission.RecruitmentSubmission;
import vn.dxlab.recruitment.submission.RecruitmentSubmissionItem;
import vn.dxlab.recruitment.submission.SubmissionStatus;

import java.time.Instant;
import java.util.List;

public record SubmissionDto(
        Long id,
        String termCode,
        String title,
        SubmissionStatus status,
        Instant sentAt,
        String recipient,
        String externalReference,
        String note,
        Long submittedById,
        String submittedByName,
        Long replacesSubmissionId,
        int positionCount,
        int totalQuota,
        List<SubmissionItemDto> items,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static SubmissionDto detail(RecruitmentSubmission s) {
        return from(s, s.getItems().stream().map(SubmissionItemDto::from).toList());
    }

    public static SubmissionDto summary(RecruitmentSubmission s) {
        return from(s, null);
    }

    private static SubmissionDto from(RecruitmentSubmission s, List<SubmissionItemDto> items) {
        return new SubmissionDto(s.getId(), s.getTermCode(), s.getTitle(), s.getStatus(), s.getSentAt(),
                s.getRecipient(), s.getExternalReference(), s.getNote(),
                s.getSubmittedBy() == null ? null : s.getSubmittedBy().getId(),
                s.getSubmittedBy() == null ? null : s.getSubmittedBy().getFullName(),
                s.getReplaces() == null ? null : s.getReplaces().getId(), s.getItems().size(),
                s.getItems().stream().mapToInt(RecruitmentSubmissionItem::getQuota).sum(), items,
                s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
