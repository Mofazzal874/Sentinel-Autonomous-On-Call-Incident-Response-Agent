package io.mofazzal.sentinel.demo;

import java.time.Instant;
import java.util.UUID;

public record DemoLiveSubmissionView(UUID publicId, String scenarioKey, String scenarioTitle,
                                     String state, String incidentStatus, Instant submittedAt,
                                     Instant completedAt, String failureReason, String runUrl) {
}
