package vn.dxlab.recruitment.interview.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@NullMarked
public record SessionDto(
        Long id,
        Long trackId,
        String trackCode,
        String trackName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        @Nullable String room,
        @Nullable String logSummary,
        List<PanelMemberDto> panel,
        /** Mọi slot của ca, kể cả slot đã dời (active = false). */
        List<SlotDto> slots,
        List<SessionWarning> warnings) {
}
