package vn.dxlab.recruitment.interview.dto;

import java.util.List;

/** {@code notScheduled}: Mã SV không xếp được vì ca hết giờ. */
public record AutoScheduleResult(List<SlotDto> scheduled, List<String> notScheduled, SessionDto session) {
}
