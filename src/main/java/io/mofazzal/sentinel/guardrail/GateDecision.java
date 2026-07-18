package io.mofazzal.sentinel.guardrail;

import java.util.Objects;
import java.util.Optional;

public record GateDecision(
        GateDecisionType type,
        String reason,
        RiskBreakdown risk
) {
    public GateDecision {
        Objects.requireNonNull(type, "type");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public Optional<RiskBreakdown> riskBreakdown() {
        return Optional.ofNullable(risk);
    }
}
