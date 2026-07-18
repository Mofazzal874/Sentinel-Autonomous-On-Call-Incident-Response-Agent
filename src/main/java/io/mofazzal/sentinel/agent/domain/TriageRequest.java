package io.mofazzal.sentinel.agent.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TriageRequest(
        UUID incidentId,
        String service,
        String summary,
        Instant occurredAt
) {
    public TriageRequest {
        Objects.requireNonNull(incidentId, "incidentId");
        service = requireText(service, "service");
        summary = requireText(summary, "summary");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
