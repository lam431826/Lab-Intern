package vn.dxlab.recruitment.audit;

import java.time.Instant;

public record HistoryEntryDto(String field, String oldValue, String newValue, String note,
                              String changedByName, Instant changedAt) {
}
