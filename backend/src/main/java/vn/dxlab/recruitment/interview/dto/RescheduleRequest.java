package vn.dxlab.recruitment.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Dời lịch sang ca khác (hoặc cùng ca, giờ khác). Bắt buộc ghi lý do. */
public record RescheduleRequest(
        @NotNull Long sessionId,
        LocalTime startTime,
        @NotBlank @Size(max = 500) String reason) {
}
