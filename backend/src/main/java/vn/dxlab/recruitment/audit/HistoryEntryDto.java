package vn.dxlab.recruitment.audit;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.Instant;

@NullMarked
public record HistoryEntryDto(String field, @Nullable String oldValue, @Nullable String newValue,
                              @Nullable String note, @Nullable String changedByName, Instant changedAt) {
}
