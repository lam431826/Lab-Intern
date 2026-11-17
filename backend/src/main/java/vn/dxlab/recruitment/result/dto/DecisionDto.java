package vn.dxlab.recruitment.result.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vn.dxlab.recruitment.result.EmailStatus;
import vn.dxlab.recruitment.result.FinalStatus;
import vn.dxlab.recruitment.result.OfferStatus;

import java.time.Instant;

@NullMarked
public record DecisionDto(
        Long id,
        FinalStatus finalStatus,
        @Nullable Long transferTrackId,
        @Nullable String transferTrackCode,
        @Nullable String note,
        boolean approved,
        @Nullable String approvedByName,
        @Nullable Instant approvedAt,
        EmailStatus emailStatus,
        OfferStatus offerStatus,
        int version) {
}
