package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;

import java.time.Instant;
import java.util.UUID;

public record RemediationRequestSnapshot(
        UUID id,
        UUID incidentId,
        UUID serviceId,
        String fingerprint,
        ServiceTier serviceTier,
        RemediationActionType actionType,
        double groundingSimilarity,
        int affectedDependents,
        boolean peakTrafficWindow,
        RemediationRequestStatus status,
        Instant approvalExpiresAt
) {
}
