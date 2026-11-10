package vn.dxlab.recruitment.submission.dto;

import vn.dxlab.recruitment.submission.RecruitmentSubmissionItem;

public record SubmissionItemDto(
        Long id,
        Long trackId,
        String trackCode,
        String trackName,
        String positionTitle,
        int quota,
        String jdContent,
        String note,
        int sortOrder,
        boolean hasFile,
        String fileName,
        String fileMimeType,
        Long fileSize,
        String fileSha256,
        int version) {

    public static SubmissionItemDto from(RecruitmentSubmissionItem item) {
        return new SubmissionItemDto(item.getId(), item.getTrack().getId(), item.getTrackCodeSnapshot(),
                item.getTrackNameSnapshot(), item.getPositionTitle(), item.getQuota(), item.getJdContent(),
                item.getNote(), item.getSortOrder(), item.hasFile(), item.getFileName(), item.getFileMimeType(),
                item.getFileSize(), item.getFileSha256(), item.getVersion());
    }
}
