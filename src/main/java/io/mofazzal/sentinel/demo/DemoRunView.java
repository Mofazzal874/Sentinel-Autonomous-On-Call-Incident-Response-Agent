package io.mofazzal.sentinel.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DemoRunView(
        UUID publicId,
        String scenarioKey,
        String scenarioTitle,
        String summary,
        String source,
        String service,
        String severity,
        String incidentStatus,
        Instant startedAt,
        String disclaimer,
        List<TimelineEntry> timeline,
        RemediationView remediation,
        EvidenceView evidence,
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

    public record EvidenceView(
            List<DeploymentEvidence> deployments,
            List<MetricSeries> metrics,
            List<LogEvidence> logs,
            List<RunbookEvidence> runbooks
    ) {
    }

    public record DeploymentEvidence(String version, String gitSha, String status,
                                     String deployedBy, Instant deployedAt) {
    }

    public record MetricSeries(String metric, List<MetricPoint> points) {
    }

    public record MetricPoint(double value, Instant recordedAt) {
    }

    public record LogEvidence(String level, String message, String traceId, Instant occurredAt) {
    }

    public record RunbookEvidence(String title, String symptom, String action, List<String> steps) {
    }
}
