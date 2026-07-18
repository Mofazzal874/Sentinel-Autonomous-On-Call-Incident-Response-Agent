package io.mofazzal.sentinel.agent.application;

import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;

import java.util.List;

/** Deterministically bounds the read-only evidence available to each incident class. */
public final class EvidenceSelectionPolicy {

    private EvidenceSelectionPolicy() {
    }

    public static List<EvidenceSignal> signalsFor(IncidentType type) {
        return switch (type) {
            case BAD_DEPLOY -> List.of(
                    EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS, EvidenceSignal.RUNBOOKS);
            case RESOURCE_EXHAUSTION, DEPENDENCY_OUTAGE -> List.of(
                    EvidenceSignal.METRICS, EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS);
            case UNKNOWN -> List.of(EvidenceSignal.LOGS, EvidenceSignal.RUNBOOKS);
        };
    }
}
