package vn.dxlab.recruitment.submission.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.submission.RecruitmentSubmission;
import vn.dxlab.recruitment.submission.RecruitmentSubmissionItem;
import vn.dxlab.recruitment.submission.SubmissionStatus;

import java.time.Instant;
import java.util.List;

@NullMarked
public record SubmissionDto(
        Long id,
        String termCode,
        String title,
        SubmissionStatus status,
        @Nullable Instant sentAt,
        @Nullable String recipient,
        @Nullable String externalReference,
        @Nullable String note,
        @Nullable Long submittedById,
        @Nullable String submittedByName,
        @Nullable Long replacesSubmissionId,
        int positionCount,
        int totalQuota,
        /** Chỉ có ở API chi tiết; danh sách trả null. */
        @Nullable List<SubmissionItemDto> items,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static SubmissionDto detail(RecruitmentSubmission s) {
        return from(s, s.getItems().stream().map(SubmissionItemDto::from).toList());
    }

    public static SubmissionDto summary(RecruitmentSubmission s) {
        return from(s, null);
    }

    private static SubmissionDto from(RecruitmentSubmission s, @Nullable List<SubmissionItemDto> items) {
        return new SubmissionDto(s.getId(), s.getTermCode(), s.getTitle(), s.getStatus(), s.getSentAt(),
                s.getRecipient(), s.getExternalReference(), s.getNote(),
                s.getSubmittedBy() == null ? null : s.getSubmittedBy().getId(),
                s.getSubmittedBy() == null ? null : s.getSubmittedBy().getFullName(),
                s.getReplaces() == null ? null : s.getReplaces().getId(), s.getItems().size(),
                s.getItems().stream().mapToInt(RecruitmentSubmissionItem::getQuota).sum(), items,
                s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
