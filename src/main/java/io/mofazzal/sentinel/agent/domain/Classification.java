package io.mofazzal.sentinel.agent.domain;

import java.util.List;
import java.util.Objects;

public record Classification(
        IncidentType type,
        List<EvidenceSignal> relevantSignals,
        String rationale
) {
    public Classification {
        Objects.requireNonNull(type, "type");
        relevantSignals = List.copyOf(Objects.requireNonNull(relevantSignals, "relevantSignals"));
        if (relevantSignals.isEmpty()) {
            throw new IllegalArgumentException("relevantSignals must not be empty");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
