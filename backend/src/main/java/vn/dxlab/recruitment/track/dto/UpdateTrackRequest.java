package vn.dxlab.recruitment.track.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mã ngạch không sửa được: các file Excel nhập vào và báo cáo dựa trên mã này. */
public record UpdateTrackRequest(
        @NotBlank(message = "Tên ngạch không được để trống") @Size(max = 100) String name,
        @Min(value = 0, message = "Chỉ tiêu không được âm") @Max(100) int quota,
        @Min(value = 0, message = "Số slot không được âm") @Max(200) int slotMin,
        @Min(value = 0, message = "Số slot không được âm") @Max(200) int slotMax,
        boolean requiresPortfolio,
        Long leadUserId) {
}
