package io.mofazzal.sentinel.guardrail;

import java.util.Optional;

public record GateEvaluation(GateDecision decision, ExecutionAuthorization authorization) {
    public Optional<ExecutionAuthorization> executionAuthorization() {
        return Optional.ofNullable(authorization);
    }
}
