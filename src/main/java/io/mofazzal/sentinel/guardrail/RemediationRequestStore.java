package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageOutcome;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RemediationRequestStore {

    static final double MIN_GROUNDING_SIMILARITY = 0.60;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final GuardrailProperties properties;

    public RemediationRequestStore(JdbcTemplate jdbc, Clock clock, GuardrailProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public UUID create(UUID incidentId, TriageOutcome outcome) {
        if (outcome.decision() != TriageOutcome.Decision.PROPOSED) {
            throw new IllegalArgumentException("Only a proposed outcome can create a remediation request");
        }
        RemediationProposal proposal = outcome.proposal();
        if (outcome.groundingSimilarity() < MIN_GROUNDING_SIMILARITY) {
            throw new IllegalArgumentException("Grounding similarity is below the execution eligibility threshold");
        }
        Integer groundedRunbook = jdbc.queryForObject("""
                SELECT count(*) FROM runbook
                WHERE id = ? AND title = ? AND action_type = ?
                """, Integer.class, outcome.groundedRunbookId(), proposal.runbookTitle(),
                proposal.actionType().name());
        if (groundedRunbook == null || groundedRunbook != 1) {
            throw new IllegalArgumentException("Grounded runbook identity, title, and action do not match");
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO remediation_request (
                    id, incident_id, runbook_id, action_type, steps, rationale, risk_notes,
                    grounding_similarity, affected_dependents, peak_traffic_window,
                    status, created_at, updated_at, approval_expires_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PENDING_DECISION', ?, ?, ?, 0)
                """, id, incidentId, outcome.groundedRunbookId(), proposal.actionType().name(),
                String.join("\n", proposal.steps()), proposal.rationale(), proposal.riskNotes(),
                outcome.groundingSimilarity(), isPeakWindow(now), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plus(properties.approvalTimeout())));
        return id;
    }

    @Transactional(readOnly = true)
    public Optional<RemediationRequestSnapshot> findByIncidentId(UUID incidentId) {
        return jdbc.query("""
                SELECT request.id, request.incident_id, incident.service_id, incident.fingerprint,
                       service.tier, request.action_type, request.grounding_similarity,
                       request.affected_dependents, request.peak_traffic_window,
                       request.status, request.approval_expires_at
                FROM remediation_request request
                JOIN incident ON incident.id = request.incident_id
                JOIN fleet_service service ON service.id = incident.service_id
                WHERE request.incident_id = ?
                """, (result, row) -> new RemediationRequestSnapshot(
                result.getObject("id", UUID.class),
                result.getObject("incident_id", UUID.class),
                result.getObject("service_id", UUID.class),
                result.getString("fingerprint"),
                ServiceTier.valueOf(result.getString("tier")),
                RemediationActionType.valueOf(result.getString("action_type")),
                result.getDouble("grounding_similarity"),
                result.getInt("affected_dependents"),
                result.getBoolean("peak_traffic_window"),
                RemediationRequestStatus.valueOf(result.getString("status")),
                result.getTimestamp("approval_expires_at").toInstant()), incidentId).stream().findFirst();
    }

    @Transactional
    public boolean transition(UUID incidentId,
                              List<RemediationRequestStatus> expected,
                              RemediationRequestStatus next,
                              RiskBreakdown risk,
                              String actor,
                              String note) {
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("expected statuses must not be empty");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(expected.size(), "?"));
        String sql = """
                UPDATE remediation_request
                SET status = ?, risk_score = COALESCE(?, risk_score),
                    risk_breakdown = COALESCE(?, risk_breakdown),
                    decided_by = COALESCE(?, decided_by), decision_note = COALESCE(?, decision_note),
                    updated_at = ?, version = version + 1
                WHERE incident_id = ? AND status IN (%s)
                """.formatted(placeholders);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(next.name());
        args.add(risk == null ? null : risk.total());
        args.add(breakdown(risk));
        args.add(actor);
        args.add(note);
        args.add(Timestamp.from(clock.instant()));
        args.add(incidentId);
        expected.forEach(status -> args.add(status.name()));
        boolean transitioned = jdbc.update(sql, args.toArray()) == 1;
        if (transitioned && risk != null) {
            jdbc.update("update incident set risk_score = ? where id = ?", risk.total(), incidentId);
        }
        return transitioned;
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpiredAwaitingApproval() {
        return jdbc.queryForList("""
                SELECT incident_id FROM remediation_request
                WHERE status = 'AWAITING_APPROVAL' AND approval_expires_at <= ?
                ORDER BY approval_expires_at LIMIT 100
                """, UUID.class, Timestamp.from(clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<UUID> findPendingDecisions() {
        return jdbc.queryForList("""
                SELECT incident_id FROM remediation_request
                WHERE status = 'PENDING_DECISION'
                ORDER BY created_at LIMIT 100
                """, UUID.class);
    }

    @Transactional(readOnly = true)
    public List<UUID> findStaleExecutions() {
        return jdbc.queryForList("""
                SELECT incident_id FROM remediation_request
                WHERE status = 'EXECUTING' AND updated_at <= ?
                ORDER BY updated_at LIMIT 100
                """, UUID.class, Timestamp.from(clock.instant().minus(properties.executionRecoveryTimeout())));
    }

    private boolean isPeakWindow(Instant now) {
        LocalTime local = now.atZone(properties.businessZone()).toLocalTime();
        return !local.isBefore(properties.peakTrafficStart()) && local.isBefore(properties.peakTrafficEnd());
    }

    private String breakdown(RiskBreakdown risk) {
        if (risk == null) {
            return null;
        }
        return risk.toString();
    }

    @Transactional(readOnly = true)
    public Optional<RemediationReview> findReview(UUID incidentId) {
        return jdbc.query("""
                SELECT request.incident_id, service.name AS service_name, request.action_type,
                       runbook.title AS runbook_title, request.steps, request.rationale,
                       request.risk_notes, request.grounding_similarity, request.risk_score,
                       request.risk_breakdown, request.status, request.approval_expires_at,
                       request.decided_by, request.decision_note
                FROM remediation_request request
                JOIN incident ON incident.id = request.incident_id
                JOIN fleet_service service ON service.id = incident.service_id
                JOIN runbook ON runbook.id = request.runbook_id
                WHERE request.incident_id = ?
                """, (result, row) -> new RemediationReview(
                result.getObject("incident_id", UUID.class),
                result.getString("service_name"),
                RemediationActionType.valueOf(result.getString("action_type")),
                result.getString("runbook_title"),
                result.getString("steps").lines().toList(),
                result.getString("rationale"),
                result.getString("risk_notes"),
                result.getDouble("grounding_similarity"),
                (Integer) result.getObject("risk_score"),
                result.getString("risk_breakdown"),
                RemediationRequestStatus.valueOf(result.getString("status")),
                result.getTimestamp("approval_expires_at").toInstant(),
                result.getString("decided_by"),
                result.getString("decision_note")), incidentId).stream().findFirst();
    }
}
