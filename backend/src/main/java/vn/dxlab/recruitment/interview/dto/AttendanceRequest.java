package vn.dxlab.recruitment.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.dxlab.recruitment.interview.Attendance;

public record AttendanceRequest(@NotNull Attendance attendance, @Size(max = 500) String note) {
}
