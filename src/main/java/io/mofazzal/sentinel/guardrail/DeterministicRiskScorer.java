package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DeterministicRiskScorer {

    static final double LOW_CONFIDENCE_THRESHOLD = 0.75;
    static final int MAX_BLAST_RADIUS_WEIGHT = 10;

    public RiskBreakdown score(RiskFacts facts) {
        Objects.requireNonNull(facts, "facts");

        int actionType = actionTypeWeight(facts.actionType());
        int serviceTier = facts.serviceTier() == ServiceTier.CRITICAL ? 4 : 1;
        int blastRadius = Math.min(facts.affectedDependents(), MAX_BLAST_RADIUS_WEIGHT);
        int confidencePenalty = facts.retrievalSimilarity() < LOW_CONFIDENCE_THRESHOLD ? 3 : 0;
        int businessHours = facts.peakTrafficWindow() ? 2 : 0;
        int total = actionType + serviceTier + blastRadius + confidencePenalty + businessHours;

        return new RiskBreakdown(
                actionType,
                serviceTier,
                blastRadius,
                confidencePenalty,
                businessHours,
                total);
    }

    private int actionTypeWeight(RemediationActionType actionType) {
        return switch (actionType) {
            case RESTART_SERVICE -> 1;
            case SCALE_OUT -> 2;
            case CLEAR_CACHE -> 3;
            case ROLLBACK_DEPLOYMENT -> 4;
        };
    }
}
