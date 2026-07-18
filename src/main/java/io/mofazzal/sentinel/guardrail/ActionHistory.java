package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

public interface ActionHistory {
    boolean alreadyActiveOrApplied(String incidentFingerprint, RemediationActionType actionType);
}
