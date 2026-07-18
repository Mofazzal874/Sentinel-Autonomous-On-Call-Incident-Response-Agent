package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.UUID;

public final class ExecutionAuthorization {

    private final UUID incidentId;
    private final UUID serviceId;
    private final String fingerprint;
    private final RemediationActionType actionType;
    private final GateDecisionType decisionType;

    ExecutionAuthorization(GateRequest request, GateDecisionType decisionType) {
        this.incidentId = request.incidentId();
        this.serviceId = request.serviceId();
        this.fingerprint = request.incidentFingerprint();
        this.actionType = request.riskFacts().actionType();
        this.decisionType = decisionType;
    }

    public boolean matches(UUID incidentId,
                           UUID serviceId,
                           String fingerprint,
                           RemediationActionType actionType,
                           GateDecisionType decisionType) {
        return this.incidentId.equals(incidentId)
                && this.serviceId.equals(serviceId)
                && this.fingerprint.equals(fingerprint)
                && this.actionType == actionType
                && this.decisionType == decisionType;
    }
}
