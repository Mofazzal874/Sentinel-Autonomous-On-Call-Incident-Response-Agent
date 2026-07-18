package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

import java.util.UUID;

public interface ServiceActionAllowlist {
    boolean permits(UUID serviceId, RemediationActionType actionType);
}
