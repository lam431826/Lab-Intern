package vn.dxlab.recruitment.result.dto;

import vn.dxlab.recruitment.result.EmailStatus;
import vn.dxlab.recruitment.result.FinalStatus;
import vn.dxlab.recruitment.result.OfferStatus;

import java.time.Instant;

public record DecisionDto(
        Long id,
        FinalStatus finalStatus,
        Long transferTrackId,
        String transferTrackCode,
        String note,
        boolean approved,
        String approvedByName,
        Instant approvedAt,
        EmailStatus emailStatus,
        OfferStatus offerStatus,
        int version) {
}
