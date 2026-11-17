package vn.dxlab.recruitment.interview.dto;

import org.jspecify.annotations.NullMarked;
import vn.dxlab.recruitment.interview.PanelRole;

@NullMarked
public record PanelMemberDto(Long userId, String fullName, String email, PanelRole panelRole) {
}
