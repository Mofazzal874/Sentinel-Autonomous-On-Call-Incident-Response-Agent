package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.guardrail.GateDecision;

import java.util.Objects;
import java.util.UUID;

public record ActionExecutionRequest(
        UUID incidentId,
        UUID serviceId,
        String fingerprint,
        RemediationActionType actionType,
        GateDecision gateDecision,
        String actor
) {
    public ActionExecutionRequest {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(serviceId, "serviceId");
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(gateDecision, "gateDecision");
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
    }
}
