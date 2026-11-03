package vn.dxlab.recruitment.result.dto;

import jakarta.validation.constraints.NotNull;
import vn.dxlab.recruitment.result.EmailStatus;
import vn.dxlab.recruitment.result.OfferStatus;

public record OfferRequest(@NotNull EmailStatus emailStatus, @NotNull OfferStatus offerStatus, @NotNull Integer version) {
}
