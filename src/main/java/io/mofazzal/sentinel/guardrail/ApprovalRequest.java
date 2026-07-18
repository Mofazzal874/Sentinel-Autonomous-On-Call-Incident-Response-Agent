package io.mofazzal.sentinel.guardrail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(
        @NotNull ApprovalDecision decision,
        @NotBlank @Size(max = 2_000) String note
) {
}
