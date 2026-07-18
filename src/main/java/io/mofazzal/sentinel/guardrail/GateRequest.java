package io.mofazzal.sentinel.guardrail;

import java.util.Objects;
import java.util.UUID;

public record GateRequest(
        UUID incidentId,
        UUID serviceId,
        String incidentFingerprint,
        RiskFacts riskFacts,
        boolean approvedByHuman
) {
    public GateRequest {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(serviceId, "serviceId");
        if (incidentFingerprint == null || incidentFingerprint.isBlank()) {
            throw new IllegalArgumentException("incidentFingerprint must not be blank");
        }
        Objects.requireNonNull(riskFacts, "riskFacts");
    }
}
