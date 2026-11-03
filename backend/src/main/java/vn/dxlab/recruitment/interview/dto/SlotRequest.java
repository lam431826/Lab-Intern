package vn.dxlab.recruitment.interview.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Xếp một ứng viên vào ca. {@code startTime = null}: lấy chỗ trống sớm nhất. */
public record SlotRequest(@NotNull Long candidateId, LocalTime startTime) {
}
