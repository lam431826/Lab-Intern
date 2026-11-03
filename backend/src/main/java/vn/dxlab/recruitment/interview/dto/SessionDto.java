package vn.dxlab.recruitment.interview.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SessionDto(
        Long id,
        Long trackId,
        String trackCode,
        String trackName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String room,
        String logSummary,
        List<PanelMemberDto> panel,
        /** Mọi slot của ca, kể cả slot đã dời (active = false). */
        List<SlotDto> slots,
        List<SessionWarning> warnings) {
}
