package io.mofazzal.sentinel.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DemoRunView(
        UUID publicId,
        String scenarioKey,
        String scenarioTitle,
        String source,
        String service,
        String severity,
        String incidentStatus,
        Instant startedAt,
        String disclaimer,
        List<TimelineEntry> timeline,
        RemediationView remediation,
        List<LedgerEntry> ledger
) {
    public record TimelineEntry(int sequence, String type, int iteration, String content, Instant recordedAt) {
    }

    public record RemediationView(
            String action,
            String runbook,
            List<String> steps,
            String rationale,
            String riskNotes,
            double groundingSimilarity,
            Integer riskScore,
            String status,
            String decisionNote
    ) {
    }

    public record LedgerEntry(
            String eventType,
            String decision,
            String mode,
            String actor,
            String details,
            Instant recordedAt
    ) {
    }
}
