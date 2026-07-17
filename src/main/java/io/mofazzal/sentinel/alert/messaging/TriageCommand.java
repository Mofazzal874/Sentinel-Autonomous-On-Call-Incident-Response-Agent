package io.mofazzal.sentinel.alert.messaging;

import io.mofazzal.sentinel.alert.api.AlertPayload;

import java.time.Instant;

public record TriageCommand(
        int schemaVersion,
        String fingerprint,
        AlertPayload payload,
        Instant receivedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static TriageCommand create(String fingerprint, AlertPayload payload, Instant receivedAt) {
        return new TriageCommand(CURRENT_SCHEMA_VERSION, fingerprint, payload, receivedAt);
    }
}
