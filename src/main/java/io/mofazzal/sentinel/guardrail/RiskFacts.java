package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;

import java.util.Objects;

public record RiskFacts(
        RemediationActionType actionType,
        ServiceTier serviceTier,
        int affectedDependents,
        double retrievalSimilarity,
        boolean peakTrafficWindow
) {
    public RiskFacts {
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(serviceTier, "serviceTier");
        if (affectedDependents < 0) {
            throw new IllegalArgumentException("affectedDependents must not be negative");
        }
        if (!Double.isFinite(retrievalSimilarity)
                || retrievalSimilarity < 0.0
                || retrievalSimilarity > 1.0) {
            throw new IllegalArgumentException("retrievalSimilarity must be between 0.0 and 1.0");
        }
    }
}
