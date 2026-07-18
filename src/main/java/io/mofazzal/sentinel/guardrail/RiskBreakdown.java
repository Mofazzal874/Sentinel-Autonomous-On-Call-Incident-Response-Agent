package io.mofazzal.sentinel.guardrail;

public record RiskBreakdown(
        int actionType,
        int serviceTier,
        int blastRadius,
        int confidencePenalty,
        int businessHours,
        int total
) {
    public RiskBreakdown {
        if (actionType < 0 || serviceTier < 0 || blastRadius < 0
                || confidencePenalty < 0 || businessHours < 0) {
            throw new IllegalArgumentException("risk components must not be negative");
        }
        int calculatedTotal = Math.addExact(
                Math.addExact(actionType, serviceTier),
                Math.addExact(Math.addExact(blastRadius, confidencePenalty), businessHours));
        if (total != calculatedTotal) {
            throw new IllegalArgumentException("total must equal the risk component sum");
        }
    }
}
