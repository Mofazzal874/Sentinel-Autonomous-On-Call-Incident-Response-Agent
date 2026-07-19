package io.mofazzal.sentinel.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("demo")
public class DemoLiveSubmissionStore {

    private final JdbcTemplate jdbc;

    public DemoLiveSubmissionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<DemoLiveSubmissionView> findExisting(String clientHash, String idempotencyHash) {
        return jdbc.query("""
                SELECT submission.public_id, template.scenario_key, submission.display_title,
                       submission.state, incident.status AS incident_status,
                       submission.submitted_at, submission.completed_at, submission.failure_reason
                FROM demo_live_submission submission
                JOIN demo_scenario_template template ON template.id = submission.template_id
                LEFT JOIN demo_run run ON run.public_id = submission.public_id
                LEFT JOIN incident ON incident.id = run.incident_id
                WHERE submission.client_hash = ? AND submission.idempotency_key_hash = ?
                """, (result, row) -> view(result), clientHash, idempotencyHash).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<DemoLiveSubmissionView> find(UUID publicId) {
        return jdbc.query("""
                SELECT submission.public_id, template.scenario_key, submission.display_title,
                       submission.state, incident.status AS incident_status,
                       submission.submitted_at, submission.completed_at, submission.failure_reason
                FROM demo_live_submission submission
                JOIN demo_scenario_template template ON template.id = submission.template_id
                LEFT JOIN demo_run run ON run.public_id = submission.public_id
                LEFT JOIN incident ON incident.id = run.incident_id
                WHERE submission.public_id = ?
                """, (result, row) -> view(result), publicId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(UUID publicId, DemoInvestigationConfiguration configuration, String fingerprint,
                       String clientHash, String idempotencyHash, Instant submittedAt) {
        jdbc.update("""
                INSERT INTO demo_live_submission (
                    public_id, template_id, fingerprint, client_hash, idempotency_key_hash,
                    state, submitted_at, service_id, scenario_type, severity, signal_intensity,
                    customer_impact, deployment_context, display_title, summary, version
                ) VALUES (?, ?, ?, ?, ?, 'ACCEPTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, publicId, configuration.template().getId(), fingerprint, clientHash, idempotencyHash,
                Timestamp.from(submittedAt), configuration.service().getId(), configuration.symptom().name(),
                configuration.severity().name(), configuration.signalIntensity().name(),
                configuration.customerImpact().name(), configuration.deploymentContext().name(),
                configuration.title(), configuration.summary());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markQueued(UUID publicId) {
        jdbc.update("""
                UPDATE demo_live_submission SET state = 'QUEUED', version = version + 1
                WHERE public_id = ? AND state = 'ACCEPTED'
                """, publicId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(String fingerprint, UUID incidentId, Instant completedAt) {
        int inserted = jdbc.update("""
                INSERT INTO demo_run (
                    public_id, scenario_key, incident_id, source, started_at, display_title, summary
                )
                SELECT submission.public_id,
                       CASE WHEN submission.display_title = template.display_name
                            THEN template.scenario_key
                            ELSE 'custom-' || lower(submission.scenario_type) END, ?, 'LIVE',
                       submission.submitted_at, submission.display_title, submission.summary
                FROM demo_live_submission submission
                JOIN demo_scenario_template template ON template.id = submission.template_id
                WHERE submission.fingerprint = ?
                ON CONFLICT DO NOTHING
                """, incidentId, fingerprint);
        int updated = jdbc.update("""
                UPDATE demo_live_submission
                SET state = 'COMPLETED', completed_at = ?, failure_reason = NULL, version = version + 1
                WHERE fingerprint = ? AND state IN ('ACCEPTED', 'QUEUED')
                """, Timestamp.from(completedAt), fingerprint);
        return inserted + updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(String fingerprint, String reason, Instant completedAt) {
        int updated = jdbc.update("""
                UPDATE demo_live_submission
                SET state = 'FAILED', completed_at = ?, failure_reason = ?, version = version + 1
                WHERE fingerprint = ? AND state IN ('ACCEPTED', 'QUEUED')
                """, Timestamp.from(completedAt), bounded(reason), fingerprint);
        return updated == 1;
    }

    private static DemoLiveSubmissionView view(java.sql.ResultSet result) throws java.sql.SQLException {
        Timestamp completed = result.getTimestamp("completed_at");
        UUID publicId = result.getObject("public_id", UUID.class);
        return new DemoLiveSubmissionView(publicId, result.getString("scenario_key"),
                result.getString("display_title"), result.getString("state"),
                result.getString("incident_status"), result.getTimestamp("submitted_at").toInstant(),
                completed == null ? null : completed.toInstant(), result.getString("failure_reason"),
                "/api/v1/demo/runs/" + publicId);
    }

    private static String bounded(String value) {
        String safe = value == null || value.isBlank() ? "Live investigation failed" : value;
        return safe.substring(0, Math.min(500, safe.length()));
    }
}
