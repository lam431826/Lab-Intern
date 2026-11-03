package vn.dxlab.recruitment.interview.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Danh sách thành viên luân chuyển mới của ca; thành viên cố định giữ nguyên. */
public record PanelRequest(@NotNull List<@NotNull Long> rotatingMemberIds) {
}
