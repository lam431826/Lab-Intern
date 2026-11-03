package vn.dxlab.recruitment.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tạo/sửa ca. {@code rotatingMemberIds} chỉ dùng khi tạo ca; Giảng viên, Người tổ chức tạo ca
 * và Lead ngạch được thêm tự động.
 */
public record SessionRequest(
        @NotNull Long trackId,
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 50) String room,
        List<Long> rotatingMemberIds) {

    public SessionRequest {
        room = room == null || room.isBlank() ? null : room.trim();
        rotatingMemberIds = rotatingMemberIds == null ? List.of() : List.copyOf(rotatingMemberIds);
    }
}
