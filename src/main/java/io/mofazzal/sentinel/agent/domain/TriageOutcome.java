package io.mofazzal.sentinel.agent.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TriageOutcome(
        Decision decision,
        RemediationProposal proposal,
        UUID groundedRunbookId,
        double groundingSimilarity,
        String reason,
        int attempts
) {
    public TriageOutcome {
        Objects.requireNonNull(decision, "decision");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        if (decision == Decision.PROPOSED && proposal == null) {
            throw new IllegalArgumentException("a proposed outcome requires a proposal");
        }
        if (decision == Decision.PROPOSED && groundedRunbookId == null) {
            throw new IllegalArgumentException("a proposed outcome requires a grounded runbook id");
        }
        if (!Double.isFinite(groundingSimilarity) || groundingSimilarity < 0.0 || groundingSimilarity > 1.0) {
            throw new IllegalArgumentException("groundingSimilarity must be between 0.0 and 1.0");
        }
        if (decision == Decision.ESCALATED
                && (proposal != null || groundedRunbookId != null || groundingSimilarity != 0.0)) {
            throw new IllegalArgumentException("an escalated outcome must not expose grounded proposal data");
        }
    }

    public Optional<RemediationProposal> optionalProposal() {
        return Optional.ofNullable(proposal);
    }

    public enum Decision {
        PROPOSED,
        ESCALATED
    }
}
