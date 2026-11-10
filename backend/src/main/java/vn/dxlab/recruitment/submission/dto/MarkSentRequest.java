package vn.dxlab.recruitment.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MarkSentRequest(
        @NotNull Instant sentAt,
        @NotBlank(message = "Đơn vị hoặc người nhận không được để trống") @Size(max = 200) String recipient,
        @Size(max = 300) String externalReference,
        @NotNull Integer version) {
}
