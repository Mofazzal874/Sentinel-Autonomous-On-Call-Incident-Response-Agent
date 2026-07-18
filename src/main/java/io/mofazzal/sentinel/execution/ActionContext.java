package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.Objects;
import java.util.UUID;

record ActionContext(
        UUID claimId,
        UUID incidentId,
        UUID serviceId,
        RemediationActionType actionType
) {
    public ActionContext {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(actionType, "actionType");
    }
}
