package vn.dxlab.recruitment.interview.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.interview.Attendance;
import vn.dxlab.recruitment.interview.SlotType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * {@code replacedById}: slot đã bị dời sang slot này. {@code timeLabel} dạng "10h - 10h15".
 */
@NullMarked
public record SlotDto(
        Long id,
        Long sessionId,
        LocalDate sessionDate,
        @Nullable String room,
        Long candidateId,
        String studentCode,
        String candidateName,
        LocalTime startTime,
        LocalTime endTime,
        String timeLabel,
        SlotType slotType,
        boolean active,
        Attendance attendance,
        @Nullable String note,
        @Nullable Long replacedById) {
}
