package vn.dxlab.recruitment.interview.dto;

import jakarta.validation.constraints.Size;

public record LogRequest(@Size(max = 10000) String logSummary) {
}
