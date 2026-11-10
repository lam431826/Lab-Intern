package vn.dxlab.recruitment.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSubmissionRequest(
        @NotBlank(message = "Mã kỳ không được để trống") @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Mã kỳ chỉ gồm chữ, số, gạch ngang hoặc gạch dưới") String termCode,
        @NotBlank(message = "Tiêu đề không được để trống") @Size(max = 200) String title,
        @Size(max = 200) String recipient,
        @Size(max = 300) String externalReference,
        @Size(max = 2000) String note,
        @NotNull Integer version) {
}
