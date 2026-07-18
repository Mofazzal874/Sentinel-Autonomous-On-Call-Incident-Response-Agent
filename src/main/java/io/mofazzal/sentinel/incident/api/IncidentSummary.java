package io.mofazzal.sentinel.incident.api;

import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record IncidentSummary(
        UUID id,
        String fingerprint,
        String service,
        IncidentStatus status,
        IncidentSeverity severity,
        BigDecimal riskScore,
        Instant createdAt,
        Instant updatedAt
) {
    public static IncidentSummary from(Incident incident) {
        return new IncidentSummary(
                incident.getId(), incident.getFingerprint(), incident.getService().getName(),
                incident.getStatus(), incident.getSeverity(),
                incident.getRiskScore(),
                incident.getCreatedAt(), incident.getUpdatedAt());
    }
}
