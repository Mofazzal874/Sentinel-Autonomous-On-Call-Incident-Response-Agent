package io.mofazzal.sentinel.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoOperationsDigitalTwinSeeder implements ApplicationRunner {

    static final int DATASET_VERSION = 1;
    private final JdbcTemplate jdbc;

    public DemoOperationsDigitalTwinSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Integer present = jdbc.queryForObject(
                "SELECT count(*) FROM demo_dataset_version WHERE version = ?", Integer.class, DATASET_VERSION);
        if (present != null && present == 1) {
            return;
        }

        seedReferenceCatalog();
        seedDependencies();
        seedDeployments();
        seedMetrics();
        seedLogs();
        seedIncidents();
        seedAgentRunsAndTranscripts();
        seedRemediationRequests();
        seedActionClaimsAndLedger();
        seedPublicRunRegistry();
        recordDatasetVersion();
    }

    private void seedReferenceCatalog() {
        jdbc.update("""
                INSERT INTO team (id, name, contact_channel) VALUES
                    ('10000000-0000-0000-0000-000000000004', 'Identity', '#team-identity')
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO fleet_service (id, name, owner_team_id, tier) VALUES
                    ('20000000-0000-0000-0000-000000000004', 'identity-api',
                     '10000000-0000-0000-0000-000000000004', 'CRITICAL'),
                    ('20000000-0000-0000-0000-000000000005', 'notification-worker',
                     '10000000-0000-0000-0000-000000000003', 'STANDARD'),
                    ('20000000-0000-0000-0000-000000000006', 'inventory-api',
                     '10000000-0000-0000-0000-000000000002', 'CRITICAL'),
                    ('20000000-0000-0000-0000-000000000007', 'order-api',
                     '10000000-0000-0000-0000-000000000002', 'CRITICAL'),
                    ('20000000-0000-0000-0000-000000000008', 'pricing-api',
                     '10000000-0000-0000-0000-000000000002', 'STANDARD'),
                    ('20000000-0000-0000-0000-000000000009', 'fraud-engine',
                     '10000000-0000-0000-0000-000000000001', 'CRITICAL'),
                    ('20000000-0000-0000-0000-000000000010', 'api-gateway',
                     '10000000-0000-0000-0000-000000000003', 'CRITICAL'),
                    ('20000000-0000-0000-0000-000000000011', 'search-api',
                     '10000000-0000-0000-0000-000000000002', 'STANDARD'),
                    ('20000000-0000-0000-0000-000000000012', 'feature-flags',
                     '10000000-0000-0000-0000-000000000003', 'STANDARD')
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO service_allowed_action (service_id, action_type)
                SELECT service.id, action.action_type
                FROM fleet_service service
                CROSS JOIN (VALUES ('RESTART_SERVICE'), ('SCALE_OUT')) action(action_type)
                WHERE service.id IN (
                    '20000000-0000-0000-0000-000000000004',
                    '20000000-0000-0000-0000-000000000005',
                    '20000000-0000-0000-0000-000000000006',
                    '20000000-0000-0000-0000-000000000007',
                    '20000000-0000-0000-0000-000000000008',
                    '20000000-0000-0000-0000-000000000009',
                    '20000000-0000-0000-0000-000000000010',
                    '20000000-0000-0000-0000-000000000011',
                    '20000000-0000-0000-0000-000000000012'
                )
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO service_allowed_action (service_id, action_type) VALUES
                    ('20000000-0000-0000-0000-000000000004', 'CLEAR_CACHE'),
                    ('20000000-0000-0000-0000-000000000006', 'ROLLBACK_DEPLOYMENT'),
                    ('20000000-0000-0000-0000-000000000007', 'ROLLBACK_DEPLOYMENT'),
                    ('20000000-0000-0000-0000-000000000008', 'CLEAR_CACHE'),
                    ('20000000-0000-0000-0000-000000000009', 'ROLLBACK_DEPLOYMENT'),
                    ('20000000-0000-0000-0000-000000000011', 'CLEAR_CACHE'),
                    ('20000000-0000-0000-0000-000000000012', 'CLEAR_CACHE')
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO runbook (id, title, symptom_description, steps, action_type) VALUES
                    ('30000000-0000-0000-0000-000000000004',
                     'Clear a stale application cache',
                     'Stale reads persist while the source of truth remains healthy.',
                     'Confirm source health; identify the bounded cache; clear one namespace; verify fresh reads and error rate.',
                     'CLEAR_CACHE'),
                    ('30000000-0000-0000-0000-000000000005',
                     'Recover from a dependency timeout',
                     'A required upstream dependency times out while local resources remain healthy.',
                     'Confirm dependency ownership; inspect upstream health; restart one unhealthy client pool; verify timeout rate.',
                     'RESTART_SERVICE'),
                    ('30000000-0000-0000-0000-000000000006',
                     'Rollback an incompatible API release',
                     'Contract errors rise immediately after a producer or consumer deployment.',
                     'Identify the contract change; select the last compatible version; perform a guarded rollback; verify both sides.',
                     'ROLLBACK_DEPLOYMENT'),
                    ('30000000-0000-0000-0000-000000000007',
                     'Scale a saturated queue consumer',
                     'Queue depth and processing latency rise while downstream capacity remains available.',
                     'Confirm downstream headroom; add one consumer replica; observe queue age; stop if dependency errors rise.',
                     'SCALE_OUT'),
                    ('30000000-0000-0000-0000-000000000008',
                     'Restart a leaking application instance',
                     'Memory growth is isolated to one instance and readiness is degrading.',
                     'Confirm instance isolation; drain traffic; restart one instance; verify memory and readiness before continuing.',
                     'RESTART_SERVICE'),
                    ('30000000-0000-0000-0000-000000000009',
                     'Clear stale feature flag state',
                     'Configuration reads disagree across instances after a flag update.',
                     'Confirm authoritative flag value; clear the service cache; verify convergence across instances.',
                     'CLEAR_CACHE'),
                    ('30000000-0000-0000-0000-000000000010',
                     'Scale an overloaded API gateway',
                     'Request concurrency, CPU, and tail latency rise together at the public edge.',
                     'Confirm downstream headroom; add one gateway replica; observe saturation and error budgets.',
                     'SCALE_OUT')
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedDependencies() {
        jdbc.update("""
                INSERT INTO service_dependency (
                    id, caller_service_id, dependency_service_id, criticality, created_at, version
                )
                SELECT md5('dependency:' || edge.caller::text || ':' || edge.target::text)::uuid,
                       edge.caller::uuid, edge.target::uuid, edge.criticality,
                       TIMESTAMPTZ '2026-07-17 00:00:00Z', 0
                FROM (VALUES
                    ('20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000007', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000006', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000008', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000001', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000009', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000005', 'DEGRADED_OK'),
                    ('20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000009', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000003', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000008', 'DEGRADED_OK'),
                    ('20000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000004', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000002', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000003', 'DEGRADED_OK'),
                    ('20000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000003', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000008', 'DEGRADED_OK'),
                    ('20000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000004', 'REQUIRED'),
                    ('20000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000012', 'DEGRADED_OK'),
                    ('20000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000001', 'REQUIRED')
                ) edge(caller, target, criticality)
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedDeployments() {
        jdbc.update("""
                WITH services AS (
                    SELECT id, name, row_number() OVER (ORDER BY id) AS service_number
                    FROM fleet_service
                )
                INSERT INTO deployment (
                    id, service_id, version, git_sha, deployed_at, deployed_by, status
                )
                SELECT md5('digital-twin:deployment:' || service.id::text || ':' || ordinal)::uuid,
                       service.id,
                       '2026.07.' || lpad((12 + ordinal)::text, 2, '0') || '.' || service.service_number,
                       md5('git:' || service.name || ':' || ordinal),
                       TIMESTAMPTZ '2026-07-16 06:00:00Z'
                           + (service.service_number * INTERVAL '17 minutes')
                           + (ordinal * INTERVAL '8 hours'),
                       CASE WHEN ordinal = 4 THEN 'emergency-release' ELSE 'release-bot' END,
                       CASE
                           WHEN ordinal = 3 AND service.service_number % 5 = 0 THEN 'FAILED'
                           WHEN ordinal = 4 AND service.service_number % 4 = 0 THEN 'ROLLED_BACK'
                           ELSE 'SUCCEEDED'
                       END
                FROM services service
                CROSS JOIN generate_series(1, 5) ordinal
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedMetrics() {
        jdbc.update("""
                WITH services AS (
                    SELECT id, row_number() OVER (ORDER BY id) AS service_number
                    FROM fleet_service
                ), metrics(metric_name) AS (VALUES
                    ('error_rate'), ('p99_latency_ms'), ('cpu_utilization'),
                    ('memory_utilization'), ('request_rate')
                )
                INSERT INTO metric_sample (id, service_id, metric_name, value, recorded_at)
                SELECT md5('digital-twin:metric:' || service.id::text || ':' || metric.metric_name || ':' || minute)::uuid,
                       service.id, metric.metric_name,
                       round((CASE metric.metric_name
                           WHEN 'error_rate' THEN
                               0.004 + service.service_number * 0.0004
                               + CASE WHEN minute BETWEEN 96 AND 112 AND service.service_number % 3 = 0 THEN 0.145 ELSE 0 END
                           WHEN 'p99_latency_ms' THEN
                               145 + service.service_number * 13 + (minute % 11) * 2
                               + CASE WHEN minute BETWEEN 96 AND 112 AND service.service_number % 3 = 0 THEN 1250 ELSE 0 END
                           WHEN 'cpu_utilization' THEN
                               0.31 + service.service_number * 0.018 + (minute % 9) * 0.004
                               + CASE WHEN minute BETWEEN 70 AND 118 AND service.service_number % 4 = 0 THEN 0.38 ELSE 0 END
                           WHEN 'memory_utilization' THEN
                               0.39 + service.service_number * 0.012 + minute * 0.00035
                               + CASE WHEN service.service_number % 5 = 0 THEN minute * 0.0012 ELSE 0 END
                           ELSE 180 + service.service_number * 27 + (minute % 15) * 4
                       END)::numeric, 6),
                       TIMESTAMPTZ '2026-07-17 00:00:00Z' + minute * INTERVAL '1 minute'
                FROM services service
                CROSS JOIN metrics metric
                CROSS JOIN generate_series(0, 179) minute
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedLogs() {
        jdbc.update("""
                WITH services AS (
                    SELECT id, name, row_number() OVER (ORDER BY id) AS service_number
                    FROM fleet_service
                )
                INSERT INTO log_event (id, service_id, level, message, occurred_at, trace_id)
                SELECT md5('digital-twin:log:' || service.id::text || ':' || event_number)::uuid,
                       service.id,
                       CASE
                           WHEN event_number % 17 = 0 THEN 'ERROR'
                           WHEN event_number % 11 = 0 THEN 'WARN'
                           ELSE 'INFO'
                       END,
                       CASE
                           WHEN event_number % 17 = 0 THEN service.name || ' request failed after upstream timeout'
                           WHEN event_number % 11 = 0 THEN service.name || ' latency crossed the warning threshold'
                           WHEN event_number % 7 = 0 THEN service.name || ' readiness probe recovered'
                           ELSE service.name || ' request completed within service objective'
                       END,
                       TIMESTAMPTZ '2026-07-17 00:00:30Z'
                           + event_number * INTERVAL '2 minutes'
                           + service.service_number * INTERVAL '3 seconds',
                       'trace-' || lpad(service.service_number::text, 2, '0') || '-'
                           || lpad(event_number::text, 3, '0')
                FROM services service
                CROSS JOIN generate_series(1, 90) event_number
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedIncidents() {
        jdbc.update("""
                WITH services AS (
                    SELECT id, row_number() OVER (ORDER BY id) AS service_number
                    FROM fleet_service
                ), generated AS (
                    SELECT incident_number,
                           CASE incident_number % 5
                               WHEN 0 THEN 'ungrounded-escalation'
                               WHEN 1 THEN 'dry-run'
                               WHEN 2 THEN 'approval-required'
                               WHEN 3 THEN 'auto-resolved'
                               ELSE 'compensated'
                           END AS outcome,
                           ((incident_number - 1) % 12) + 1 AS service_number
                    FROM generate_series(1, 27) incident_number
                )
                INSERT INTO incident (
                    id, fingerprint, service_id, status, severity, correlated_deployment_id,
                    proposed_runbook_id, risk_score, created_at, updated_at, version
                )
                SELECT md5('digital-twin:incident:' || generated.incident_number)::uuid,
                       'demo:v2:' || generated.outcome || ':' || lpad(generated.incident_number::text, 2, '0'),
                       service.id,
                       CASE generated.outcome
                           WHEN 'approval-required' THEN 'AWAITING_APPROVAL'
                           WHEN 'auto-resolved' THEN 'RESOLVED'
                           ELSE 'ESCALATED'
                       END,
                       CASE generated.incident_number % 4
                           WHEN 0 THEN 'SEV1' WHEN 1 THEN 'SEV2' WHEN 2 THEN 'SEV3' ELSE 'SEV4'
                       END,
                       CASE WHEN generated.incident_number % 3 = 0 THEN (
                           SELECT deployment.id FROM deployment
                           WHERE deployment.service_id = service.id
                             AND deployment.deployed_at <= TIMESTAMPTZ '2026-07-17 00:10:00Z'
                                 + generated.incident_number * INTERVAL '6 minutes'
                           ORDER BY deployment.deployed_at DESC LIMIT 1
                       ) END,
                       CASE generated.outcome
                           WHEN 'ungrounded-escalation' THEN NULL
                           ELSE CASE generated.incident_number % 4
                               WHEN 0 THEN '30000000-0000-0000-0000-000000000004'::uuid
                               WHEN 1 THEN '30000000-0000-0000-0000-000000000001'::uuid
                               WHEN 2 THEN '30000000-0000-0000-0000-000000000002'::uuid
                               ELSE '30000000-0000-0000-0000-000000000003'::uuid
                           END
                       END,
                       CASE generated.outcome
                           WHEN 'ungrounded-escalation' THEN NULL
                           WHEN 'dry-run' THEN 6
                           WHEN 'approval-required' THEN 12
                           WHEN 'auto-resolved' THEN 4
                           ELSE 9
                       END,
                       TIMESTAMPTZ '2026-07-17 00:10:00Z' + generated.incident_number * INTERVAL '6 minutes',
                       TIMESTAMPTZ '2026-07-17 00:10:00Z' + generated.incident_number * INTERVAL '6 minutes' + INTERVAL '2 minutes',
                       0
                FROM generated
                JOIN services service ON service.service_number = generated.service_number
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedAgentRunsAndTranscripts() {
        jdbc.update("""
                INSERT INTO agent_run (
                    id, incident_id, status, started_at, completed_at, outcome_reason,
                    attempt_count, next_sequence, version
                )
                SELECT md5('digital-twin:run:' || incident.id::text)::uuid,
                       incident.id,
                       CASE WHEN incident.fingerprint LIKE '%ungrounded-escalation%' THEN 'ESCALATED' ELSE 'PROPOSED' END,
                       incident.created_at + INTERVAL '8 seconds',
                       incident.created_at + INTERVAL '105 seconds',
                       CASE
                           WHEN incident.fingerprint LIKE '%ungrounded-escalation%'
                               THEN 'No sufficiently grounded remediation; human investigation required'
                           ELSE 'Grounded proposal completed deterministic evaluation'
                       END,
                       CASE WHEN incident.fingerprint LIKE '%approval-required%' THEN 2 ELSE 1 END,
                       6, 0
                FROM incident
                WHERE fingerprint LIKE 'demo:v2:%'
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO agent_transcript_entry (
                    id, run_id, sequence_number, entry_type, iteration, content, recorded_at
                )
                SELECT md5('digital-twin:transcript:' || run.id::text || ':' || stage.sequence)::uuid,
                       run.id, stage.sequence, stage.entry_type,
                       CASE WHEN stage.sequence IN (3, 4) THEN LEAST(run.attempt_count, 3) ELSE 0 END,
                       CASE stage.sequence
                           WHEN 1 THEN 'Classified ' || service.name || ' incident from bounded alert facts and deterministic signal selection.'
                           WHEN 2 THEN 'Correlated deployment history, five metric series, clustered logs, service dependencies, and ranked runbooks.'
                           WHEN 3 THEN CASE
                               WHEN incident.fingerprint LIKE '%ungrounded-escalation%'
                                   THEN 'No remediation proposal produced because authoritative evidence was incomplete.'
                               ELSE 'Proposed one reversible, allowlisted action grounded in the selected runbook.'
                           END
                           WHEN 4 THEN CASE
                               WHEN incident.fingerprint LIKE '%compensated%'
                                   THEN 'Evaluator bounded the action and required post-action verification plus compensation.'
                               WHEN incident.fingerprint LIKE '%ungrounded-escalation%'
                                   THEN 'Safety critique rejected speculative remediation and selected escalation.'
                               ELSE 'Evaluator accepted evidence coverage, bounded scope, and explicit verification steps.'
                           END
                           ELSE CASE
                               WHEN incident.fingerprint LIKE '%dry-run%' THEN 'DRY_RUN — mutation was recorded but not performed.'
                               WHEN incident.fingerprint LIKE '%approval-required%' THEN 'REQUIRE_APPROVAL — waiting for an SRE decision.'
                               WHEN incident.fingerprint LIKE '%auto-resolved%' THEN 'AUTO_EXECUTE — simulated action applied and verified.'
                               WHEN incident.fingerprint LIKE '%compensated%' THEN 'COMPENSATED — verification failed and the simulated effect was reversed.'
                               ELSE 'ESCALATED — missing grounding is a hard safety stop.'
                           END
                       END,
                       run.started_at + stage.sequence * INTERVAL '19 seconds'
                FROM agent_run run
                JOIN incident ON incident.id = run.incident_id
                JOIN fleet_service service ON service.id = incident.service_id
                CROSS JOIN (VALUES
                    (1, 'CLASSIFICATION'), (2, 'EVIDENCE'), (3, 'PROPOSAL'),
                    (4, 'CRITIQUE'), (5, 'OUTCOME')
                ) stage(sequence, entry_type)
                WHERE incident.fingerprint LIKE 'demo:v2:%'
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedRemediationRequests() {
        jdbc.update("""
                INSERT INTO remediation_request (
                    id, incident_id, runbook_id, action_type, steps, rationale, risk_notes,
                    grounding_similarity, affected_dependents, peak_traffic_window,
                    risk_score, risk_breakdown, status, created_at, updated_at,
                    approval_expires_at, decided_by, decision_note, version
                )
                SELECT md5('digital-twin:request:' || incident.id::text)::uuid,
                       incident.id, runbook.id, runbook.action_type,
                       runbook.steps,
                       'Synthetic telemetry and dependency evidence support one bounded ' || lower(runbook.action_type) || ' action.',
                       'The deterministic gate remains authoritative; verify recovery and compensate on failure.',
                       CASE WHEN incident.fingerprint LIKE '%compensated%' THEN 0.71 ELSE 0.88 END,
                       (substring(incident.fingerprint from '([0-9]+)$')::int % 4),
                       true,
                       incident.risk_score::integer,
                       'digitalTwin=true,total=' || incident.risk_score::integer,
                       CASE
                           WHEN incident.fingerprint LIKE '%dry-run%' THEN 'DRY_RUN'
                           WHEN incident.fingerprint LIKE '%approval-required%' THEN 'AWAITING_APPROVAL'
                           WHEN incident.fingerprint LIKE '%auto-resolved%' THEN 'COMPLETED'
                           ELSE 'ESCALATED'
                       END,
                       incident.created_at + INTERVAL '90 seconds',
                       incident.updated_at,
                       CASE
                           WHEN incident.fingerprint LIKE '%approval-required%' THEN TIMESTAMPTZ '2099-01-01 00:00:00Z'
                           ELSE incident.created_at + INTERVAL '1 day'
                       END,
                       CASE WHEN incident.fingerprint LIKE '%auto-resolved%' THEN 'AGENT' ELSE NULL END,
                       CASE
                           WHEN incident.fingerprint LIKE '%dry-run%' THEN 'dry-run mode is enabled'
                           WHEN incident.fingerprint LIKE '%approval-required%' THEN 'risk exceeds automatic threshold'
                           WHEN incident.fingerprint LIKE '%auto-resolved%' THEN 'simulated action completed and verification passed'
                           ELSE 'verification failed; simulated compensation completed'
                       END,
                       0
                FROM incident
                JOIN runbook ON runbook.id = incident.proposed_runbook_id
                WHERE incident.fingerprint LIKE 'demo:v2:%'
                  AND incident.fingerprint NOT LIKE '%ungrounded-escalation%'
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedActionClaimsAndLedger() {
        jdbc.update("""
                INSERT INTO action_claim (
                    id, incident_id, fingerprint, action_type, state,
                    created_at, completed_at, result, version
                )
                SELECT md5('digital-twin:claim:' || incident.id::text)::uuid,
                       incident.id, incident.fingerprint, runbook.action_type,
                       CASE WHEN incident.fingerprint LIKE '%compensated%' THEN 'COMPENSATED' ELSE 'APPLIED' END,
                       incident.created_at + INTERVAL '112 seconds',
                       incident.created_at + INTERVAL '118 seconds',
                       CASE
                           WHEN incident.fingerprint LIKE '%compensated%' THEN 'Simulated effect reversed after failed verification'
                           ELSE 'Simulated effect applied and verified'
                       END,
                       0
                FROM incident
                JOIN runbook ON runbook.id = incident.proposed_runbook_id
                WHERE incident.fingerprint LIKE 'demo:v2:%'
                  AND (incident.fingerprint LIKE '%auto-resolved%'
                       OR incident.fingerprint LIKE '%compensated%')
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                WITH event_rows AS (
                    SELECT incident.id AS incident_id, incident.fingerprint,
                           runbook.action_type, claim.id AS claim_id,
                           event.sequence, event.event_type,
                           CASE
                               WHEN incident.fingerprint LIKE '%dry-run%' THEN 'DRY_RUN'
                               WHEN incident.fingerprint LIKE '%approval-required%' THEN 'REQUIRE_APPROVAL'
                               WHEN incident.fingerprint LIKE '%auto-resolved%' THEN 'AUTO_EXECUTE'
                               ELSE 'AUTO_EXECUTE'
                           END AS decision,
                           CASE
                               WHEN incident.fingerprint LIKE '%dry-run%' THEN 'DRY_RUN'
                               WHEN incident.fingerprint LIKE '%auto-resolved%' OR incident.fingerprint LIKE '%compensated%'
                                   THEN 'AUTOMATIC'
                               ELSE 'NONE'
                           END AS mode,
                           incident.risk_score::integer AS risk_score,
                           incident.created_at,
                           event.details
                    FROM incident
                    JOIN runbook ON runbook.id = incident.proposed_runbook_id
                    LEFT JOIN action_claim claim ON claim.incident_id = incident.id
                    CROSS JOIN LATERAL (
                        SELECT * FROM (VALUES
                            (1, CASE
                                WHEN incident.fingerprint LIKE '%dry-run%' THEN 'DRY_RUN'
                                WHEN incident.fingerprint LIKE '%approval-required%' THEN 'APPROVAL_REQUESTED'
                                ELSE 'DECIDED' END,
                             CASE
                                WHEN incident.fingerprint LIKE '%dry-run%' THEN 'Dry-run prevented infrastructure mutation'
                                WHEN incident.fingerprint LIKE '%approval-required%' THEN 'Risk requires an SRE decision'
                                ELSE 'Deterministic gate authorized a simulated action' END),
                            (2, 'IN_PROGRESS', 'Durable claim committed before the simulated side effect'),
                            (3, 'APPLIED', 'Simulated side effect applied idempotently'),
                            (4, 'COMPENSATION_STARTED', 'Post-action verification failed; compensation started'),
                            (5, 'COMPENSATED', 'Simulated side effect was reversed')
                        ) value(sequence, event_type, details)
                        WHERE sequence = 1
                           OR (incident.fingerprint LIKE '%auto-resolved%' AND sequence <= 3)
                           OR (incident.fingerprint LIKE '%compensated%')
                    ) event
                    WHERE incident.fingerprint LIKE 'demo:v2:%'
                      AND incident.fingerprint NOT LIKE '%ungrounded-escalation%'
                )
                INSERT INTO action_ledger (
                    id, claim_id, incident_id, fingerprint, action_type, event_type,
                    decision, risk_score, risk_breakdown, mode, actor, details,
                    recorded_at, compensation_of
                )
                SELECT md5('digital-twin:ledger:' || incident_id::text || ':' || sequence)::uuid,
                       CASE WHEN sequence >= 2 THEN claim_id ELSE NULL END,
                       incident_id, fingerprint, action_type, event_type, decision,
                       risk_score, 'digitalTwin=true,total=' || risk_score, mode,
                       'AGENT', details, created_at + (108 + sequence * 4) * INTERVAL '1 second',
                       CASE WHEN sequence >= 4
                           THEN md5('digital-twin:ledger:' || incident_id::text || ':3')::uuid
                           ELSE NULL END
                FROM event_rows
                ON CONFLICT DO NOTHING
                """);
    }

    private void seedPublicRunRegistry() {
        jdbc.update("""
                INSERT INTO demo_run (
                    public_id, scenario_key, incident_id, source, started_at, display_title, summary
                )
                SELECT md5('digital-twin:public:' || incident.id::text)::uuid,
                       substring(incident.fingerprint from 'demo:v2:([^:]+):'),
                       incident.id, 'RECORDED', incident.created_at,
                       CASE
                           WHEN incident.fingerprint LIKE '%ungrounded-escalation%'
                               THEN service.name || ': insufficient evidence'
                           WHEN incident.fingerprint LIKE '%dry-run%'
                               THEN service.name || ': guarded dry-run'
                           WHEN incident.fingerprint LIKE '%approval-required%'
                               THEN service.name || ': approval required'
                           WHEN incident.fingerprint LIKE '%auto-resolved%'
                               THEN service.name || ': bounded recovery'
                           ELSE service.name || ': compensated recovery'
                       END,
                       CASE
                           WHEN incident.fingerprint LIKE '%ungrounded-escalation%'
                               THEN 'Evidence remained ambiguous, so Sentinel escalated without proposing an action.'
                           WHEN incident.fingerprint LIKE '%dry-run%'
                               THEN 'A grounded action reached the gate, where public dry-run policy prevented mutation.'
                           WHEN incident.fingerprint LIKE '%approval-required%'
                               THEN 'Deterministic risk exceeded the automatic threshold and now requires an SRE.'
                           WHEN incident.fingerprint LIKE '%auto-resolved%'
                               THEN 'A low-risk simulated action was claimed, applied once, and verified.'
                           ELSE 'Verification failed after a simulated action, so compensation reversed its effect.'
                       END
                FROM incident
                JOIN fleet_service service ON service.id = incident.service_id
                WHERE incident.fingerprint LIKE 'demo:v2:%'
                ON CONFLICT DO NOTHING
                """);
    }

    private void recordDatasetVersion() {
        jdbc.update("""
                INSERT INTO demo_dataset_version (version, generator_name, records, seeded_at)
                VALUES (?, 'DemoOperationsDigitalTwinSeeder', jsonb_build_object(
                    'teams', (SELECT count(*) FROM team),
                    'services', (SELECT count(*) FROM fleet_service),
                    'dependencies', (SELECT count(*) FROM service_dependency),
                    'deployments', (SELECT count(*) FROM deployment),
                    'metricSamples', (SELECT count(*) FROM metric_sample),
                    'logEvents', (SELECT count(*) FROM log_event),
                    'incidents', (SELECT count(*) FROM incident),
                    'runbooks', (SELECT count(*) FROM runbook)
                ), CURRENT_TIMESTAMP)
                """, DATASET_VERSION);
    }
}
