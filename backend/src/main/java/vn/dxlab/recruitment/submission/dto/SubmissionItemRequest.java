package vn.dxlab.recruitment.submission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmissionItemRequest(
        @NotNull(message = "Chưa chọn ngạch") Long trackId,
        @NotBlank(message = "Tên vị trí không được để trống") @Size(max = 200) String positionTitle,
        @Min(value = 1, message = "Chỉ tiêu phải lớn hơn 0") @Max(value = 1000) int quota,
        @Size(max = 100000, message = "Nội dung JD tối đa 100.000 ký tự") String jdContent,
        @Size(max = 2000) String note,
        @Min(0) int sortOrder,
        Integer version) {
}
