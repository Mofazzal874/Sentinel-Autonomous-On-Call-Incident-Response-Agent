package io.mofazzal.sentinel.demo;

import java.time.Instant;
import java.util.UUID;

public record DemoRunSummary(
        UUID publicId,
        String scenarioKey,
        String scenarioTitle,
        String source,
        String service,
        String severity,
        String incidentStatus,
        Instant startedAt
) {
}
