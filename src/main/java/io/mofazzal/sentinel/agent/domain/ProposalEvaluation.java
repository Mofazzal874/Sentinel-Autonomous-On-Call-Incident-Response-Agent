package io.mofazzal.sentinel.agent.domain;

public record ProposalEvaluation(boolean passed, String feedback) {
    public ProposalEvaluation {
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("feedback must not be blank");
        }
    }
}
