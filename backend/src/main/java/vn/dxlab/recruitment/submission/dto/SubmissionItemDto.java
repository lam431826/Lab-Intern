package vn.dxlab.recruitment.submission.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.submission.RecruitmentSubmissionItem;

@NullMarked
public record SubmissionItemDto(
        Long id,
        Long trackId,
        String trackCode,
        String trackName,
        String positionTitle,
        int quota,
        @Nullable String jdContent,
        @Nullable String note,
        int sortOrder,
        boolean hasFile,
        @Nullable String fileName,
        @Nullable String fileMimeType,
        @Nullable Long fileSize,
        @Nullable String fileSha256,
        int version) {

    public static SubmissionItemDto from(RecruitmentSubmissionItem item) {
        return new SubmissionItemDto(item.getId(), item.getTrack().getId(), item.getTrackCodeSnapshot(),
                item.getTrackNameSnapshot(), item.getPositionTitle(), item.getQuota(), item.getJdContent(),
                item.getNote(), item.getSortOrder(), item.hasFile(), item.getFileName(), item.getFileMimeType(),
                item.getFileSize(), item.getFileSha256(), item.getVersion());
    }
}
