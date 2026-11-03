package vn.dxlab.recruitment.interview.dto;

import vn.dxlab.recruitment.interview.PanelRole;

public record PanelMemberDto(Long userId, String fullName, String email, PanelRole panelRole) {
}
