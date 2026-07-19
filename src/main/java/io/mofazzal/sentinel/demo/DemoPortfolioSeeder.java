package io.mofazzal.sentinel.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
@Profile("demo")
public class DemoPortfolioSeeder implements ApplicationRunner {

    static final UUID GROUNDED_INCIDENT = UUID.fromString("41000000-0000-0000-0000-000000000001");
    static final UUID AMBIGUOUS_INCIDENT = UUID.fromString("41000000-0000-0000-0000-000000000002");
    static final UUID APPROVAL_INCIDENT = UUID.fromString("41000000-0000-0000-0000-000000000003");

    private static final UUID PAYMENTS = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CHECKOUT = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CATALOG = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID ROLLBACK_RUNBOOK = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SCALE_RUNBOOK = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID BAD_DEPLOYMENT = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;

    public DemoPortfolioSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        seedCorrelatedDeployment();
        seedGroundedDryRun();
        seedUngroundedEscalation();
        seedHumanApprovalBoundary();
    }

    private void seedCorrelatedDeployment() {
        jdbc.update("""
                INSERT INTO deployment (
                    id, service_id, version, git_sha, deployed_at, deployed_by, status
                ) VALUES (?, ?, '2026.07.18.4', 'demo-bad-release-20260718', ?, 'release-bot', 'SUCCEEDED')
                ON CONFLICT DO NOTHING
                """, BAD_DEPLOYMENT, PAYMENTS, timestamp("2026-07-18T18:03:00Z"));
    }

    private void seedGroundedDryRun() {
        UUID runId = uuid("42000000-0000-0000-0000-000000000001");
        UUID requestId = uuid("43000000-0000-0000-0000-000000000001");
        Instant startedAt = instant("2026-07-18T18:09:12Z");
        insertIncident(GROUNDED_INCIDENT, "demo:payments:bad-release", PAYMENTS, "ESCALATED", "SEV1",
                BAD_DEPLOYMENT, ROLLBACK_RUNBOOK, 8, startedAt, instant("2026-07-18T18:10:41Z"));
        insertRun(runId, GROUNDED_INCIDENT, "PROPOSED", startedAt, instant("2026-07-18T18:10:38Z"),
                "Grounded rollback proposal passed evaluation", 1, 6);
        insertTranscript(runId, 1, "CLASSIFICATION", 0,
                "BAD_DEPLOY — error rate and p99 latency rose within six minutes of release 2026.07.18.4.", startedAt.plusSeconds(8));
        insertTranscript(runId, 2, "EVIDENCE", 0,
                "Deploy: 2026.07.18.4 by release-bot. Metrics: error rate 0.8% → 18%; p99 220ms → 1850ms. Logs: provider timeouts and circuit breaker opened.", startedAt.plusSeconds(31));
        insertTranscript(runId, 3, "EVIDENCE", 0,
                "Runbook match: Rollback a faulty service deployment (cosine similarity 0.92).", startedAt.plusSeconds(44));
        insertTranscript(runId, 4, "PROPOSAL", 1,
                "Propose ROLLBACK_DEPLOYMENT to the last known-good release, then verify error rate and p99 latency.", startedAt.plusSeconds(68));
        insertTranscript(runId, 5, "CRITIQUE", 1,
                "Accepted: proposal is reversible, allowlisted, bounded, and directly grounded in correlated evidence.", startedAt.plusSeconds(82));
        insertRequest(requestId, GROUNDED_INCIDENT, ROLLBACK_RUNBOOK, "ROLLBACK_DEPLOYMENT",
                "Confirm deployment correlation\nSelect last known-good release\nSimulate guarded rollback\nVerify recovery metrics",
                "The degradation began immediately after release 2026.07.18.4.",
                "Critical service; deterministic gate must authorize any mutation.", 0.92, 8,
                "actionType=3,serviceTier=2,blastRadius=0,confidencePenalty=1,businessHours=2,total=8",
                "DRY_RUN", startedAt.plusSeconds(86), "AGENT", "dry-run mode is enabled");
        insertTranscript(runId, 6, "OUTCOME", 1,
                "DRY_RUN — the gate recorded the intended rollback and prevented an infrastructure mutation.", startedAt.plusSeconds(89));
        insertLedger(uuid("44000000-0000-0000-0000-000000000001"), GROUNDED_INCIDENT,
                "demo:payments:bad-release", "ROLLBACK_DEPLOYMENT", "DRY_RUN", "DRY_RUN", 8,
                "AGENT", "dry-run mode is enabled", startedAt.plusSeconds(89));
        insertDemoRun(uuid("45000000-0000-0000-0000-000000000001"), "faulty-deployment",
                GROUNDED_INCIDENT, startedAt);
    }

    private void seedUngroundedEscalation() {
        UUID runId = uuid("42000000-0000-0000-0000-000000000002");
        Instant startedAt = instant("2026-07-18T19:24:03Z");
        insertIncident(AMBIGUOUS_INCIDENT, "demo:checkout:dependency-unknown", CHECKOUT, "ESCALATED", "SEV2",
                null, null, null, startedAt, instant("2026-07-18T19:24:47Z"));
        insertRun(runId, AMBIGUOUS_INCIDENT, "ESCALATED", startedAt, instant("2026-07-18T19:24:47Z"),
                "No sufficiently grounded remediation; human investigation required", 0, 4);
        insertTranscript(runId, 1, "CLASSIFICATION", 0,
                "DEPENDENCY_OUTAGE — checkout failures coincide with an upstream inventory timeout, but ownership is uncertain.", startedAt.plusSeconds(9));
        insertTranscript(runId, 2, "EVIDENCE", 0,
                "No recent checkout-web deployment. Local CPU and memory are normal. Logs identify upstream inventory timeouts without a matching authoritative runbook.", startedAt.plusSeconds(22));
        insertTranscript(runId, 3, "OUTCOME", 0,
                "ESCALATED — missing grounded remediation is a safety stop, not permission to invent an action.", startedAt.plusSeconds(44));
        insertDemoRun(uuid("45000000-0000-0000-0000-000000000002"), "ambiguous-dependency",
                AMBIGUOUS_INCIDENT, startedAt);
    }

    private void seedHumanApprovalBoundary() {
        UUID runId = uuid("42000000-0000-0000-0000-000000000003");
        UUID requestId = uuid("43000000-0000-0000-0000-000000000003");
        Instant startedAt = instant("2026-07-18T20:41:18Z");
        insertIncident(APPROVAL_INCIDENT, "demo:catalog:saturation", CATALOG, "AWAITING_APPROVAL", "SEV2",
                null, SCALE_RUNBOOK, 10, startedAt, instant("2026-07-18T20:42:32Z"));
        insertRun(runId, APPROVAL_INCIDENT, "PROPOSED", startedAt, instant("2026-07-18T20:42:27Z"),
                "Grounded scale-out proposal requires a human decision", 2, 5);
        insertTranscript(runId, 1, "CLASSIFICATION", 0,
                "RESOURCE_EXHAUSTION — sustained CPU and request concurrency are high without a correlated release.", startedAt.plusSeconds(10));
        insertTranscript(runId, 2, "EVIDENCE", 0,
                "CPU remained above 91% for 15 minutes; p99 latency reached 940ms; downstream capacity check is inconclusive.", startedAt.plusSeconds(31));
        insertTranscript(runId, 3, "PROPOSAL", 1,
                "Propose SCALE_OUT by one replica and observe saturation, latency, and downstream health.", startedAt.plusSeconds(52));
        insertTranscript(runId, 4, "CRITIQUE", 2,
                "Accepted after bounding the change to one replica; residual downstream risk requires human review.", startedAt.plusSeconds(69));
        insertRequest(requestId, APPROVAL_INCIDENT, SCALE_RUNBOOK, "SCALE_OUT",
                "Confirm downstream capacity\nIncrease replicas by one\nObserve CPU and p99 latency\nStop if dependency errors rise",
                "Sustained saturation with no bad deployment supports a bounded scale-out.",
                "Downstream capacity is uncertain, so automatic execution is not permitted.", 0.86, 10,
                "actionType=4,serviceTier=0,blastRadius=2,confidencePenalty=1,businessHours=3,total=10",
                "AWAITING_APPROVAL", startedAt.plusSeconds(72), null, "risk exceeds automatic threshold");
        insertTranscript(runId, 5, "OUTCOME", 2,
                "REQUIRE_APPROVAL — no action claim exists until an SRE approves and the gate re-checks every invariant.", startedAt.plusSeconds(74));
        insertLedger(uuid("44000000-0000-0000-0000-000000000003"), APPROVAL_INCIDENT,
                "demo:catalog:saturation", "SCALE_OUT", "APPROVAL_REQUESTED", "NONE", 10,
                "AGENT", "risk exceeds automatic threshold", startedAt.plusSeconds(74));
        insertDemoRun(uuid("45000000-0000-0000-0000-000000000003"), "capacity-approval",
                APPROVAL_INCIDENT, startedAt);
    }

    private void insertIncident(UUID id, String fingerprint, UUID serviceId, String status, String severity,
                                UUID deploymentId, UUID runbookId, Integer risk, Instant created, Instant updated) {
        jdbc.update("""
                INSERT INTO incident (
                    id, fingerprint, service_id, status, severity, correlated_deployment_id,
                    proposed_runbook_id, risk_score, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, id, fingerprint, serviceId, status, severity, deploymentId, runbookId, risk,
                Timestamp.from(created), Timestamp.from(updated));
    }

    private void insertRun(UUID id, UUID incidentId, String status, Instant started, Instant completed,
                           String reason, int attempts, int nextSequence) {
        jdbc.update("""
                INSERT INTO agent_run (
                    id, incident_id, status, started_at, completed_at, outcome_reason,
                    attempt_count, next_sequence, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, id, incidentId, status, Timestamp.from(started), Timestamp.from(completed), reason,
                attempts, nextSequence);
    }

    private void insertTranscript(UUID runId, int sequence, String type, int iteration,
                                  String content, Instant recordedAt) {
        jdbc.update("""
                INSERT INTO agent_transcript_entry (
                    id, run_id, sequence_number, entry_type, iteration, content, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.nameUUIDFromBytes((runId + ":" + sequence).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                runId, sequence, type, iteration, content, Timestamp.from(recordedAt));
    }

    private void insertRequest(UUID id, UUID incidentId, UUID runbookId, String actionType,
                               String steps, String rationale, String riskNotes, double similarity,
                               int risk, String breakdown, String status, Instant created,
                               String actor, String note) {
        jdbc.update("""
                INSERT INTO remediation_request (
                    id, incident_id, runbook_id, action_type, steps, rationale, risk_notes,
                    grounding_similarity, affected_dependents, peak_traffic_window,
                    risk_score, risk_breakdown, status, created_at, updated_at,
                    approval_expires_at, decided_by, decision_note, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, true, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, id, incidentId, runbookId, actionType, steps, rationale, riskNotes, similarity,
                risk, breakdown, status, Timestamp.from(created), Timestamp.from(created),
                Timestamp.from(instant("2099-01-01T00:00:00Z")), actor, note);
    }

    private void insertLedger(UUID id, UUID incidentId, String fingerprint, String actionType,
                              String eventType, String mode, int risk, String actor,
                              String details, Instant recordedAt) {
        jdbc.update("""
                INSERT INTO action_ledger (
                    id, claim_id, incident_id, fingerprint, action_type, event_type, decision,
                    risk_score, risk_breakdown, mode, actor, details, recorded_at, compensation_of
                ) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                ON CONFLICT DO NOTHING
                """, id, incidentId, fingerprint, actionType, eventType,
                eventType.equals("DRY_RUN") ? "DRY_RUN" : "REQUIRE_APPROVAL", risk,
                "Recorded deterministic demo risk total=" + risk, mode, actor, details,
                Timestamp.from(recordedAt));
    }

    private void insertDemoRun(UUID publicId, String scenarioKey, UUID incidentId, Instant startedAt) {
        jdbc.update("""
                INSERT INTO demo_run (public_id, scenario_key, incident_id, source, started_at)
                VALUES (?, ?, ?, 'RECORDED', ?)
                ON CONFLICT DO NOTHING
                """, publicId, scenarioKey, incidentId, Timestamp.from(startedAt));
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static Timestamp timestamp(String value) {
        return Timestamp.from(instant(value));
    }
}
