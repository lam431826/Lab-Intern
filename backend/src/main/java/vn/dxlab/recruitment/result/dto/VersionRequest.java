package vn.dxlab.recruitment.result.dto;

import jakarta.validation.constraints.NotNull;

public record VersionRequest(@NotNull Integer version) {
}
