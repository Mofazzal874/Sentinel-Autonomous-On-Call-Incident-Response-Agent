package io.mofazzal.sentinel.agent.domain;

import java.util.Objects;
import java.util.Optional;

public record TriageOutcome(
        Decision decision,
        RemediationProposal proposal,
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
        if (decision == Decision.ESCALATED && proposal != null) {
            throw new IllegalArgumentException("an escalated outcome must not expose a proposal");
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
