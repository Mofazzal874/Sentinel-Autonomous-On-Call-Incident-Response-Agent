package io.mofazzal.sentinel.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("demo")
public class DemoRunQueryService {

    static final String DISCLAIMER = "Deterministic synthetic operations data; no customer or production data.";
    private final JdbcTemplate jdbc;

    public DemoRunQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<DemoRunSummary> list() {
        return jdbc.query("""
                SELECT demo.public_id, demo.scenario_key, demo.display_title, demo.summary,
                       demo.source, demo.started_at,
                       service.name AS service_name, incident.severity, incident.status
                FROM demo_run demo
                JOIN incident ON incident.id = demo.incident_id
                JOIN fleet_service service ON service.id = incident.service_id
                ORDER BY demo.started_at DESC
                LIMIT 50
                """, (result, row) -> new DemoRunSummary(
                result.getObject("public_id", UUID.class),
                result.getString("scenario_key"),
                result.getString("display_title"),
                result.getString("summary"),
                result.getString("source"),
                result.getString("service_name"),
                result.getString("severity"),
                result.getString("status"),
                result.getTimestamp("started_at").toInstant()));
    }

    @Transactional(readOnly = true)
    public Optional<DemoRunView> find(UUID publicId) {
        return jdbc.query("""
                SELECT demo.public_id, demo.scenario_key, demo.display_title, demo.summary,
                       demo.source, demo.started_at,
                       demo.incident_id, service.name AS service_name,
                       incident.severity, incident.status
                FROM demo_run demo
                JOIN incident ON incident.id = demo.incident_id
                JOIN fleet_service service ON service.id = incident.service_id
                WHERE demo.public_id = ?
                """, (result, row) -> {
            UUID incidentId = result.getObject("incident_id", UUID.class);
            return new DemoRunView(
                    result.getObject("public_id", UUID.class),
                    result.getString("scenario_key"),
                    result.getString("display_title"),
                    result.getString("summary"),
                    result.getString("source"),
                    result.getString("service_name"),
                    result.getString("severity"),
                    result.getString("status"),
                    result.getTimestamp("started_at").toInstant(),
                    DISCLAIMER,
                    timeline(incidentId),
                    remediation(incidentId).orElse(null),
                    ledger(incidentId));
        }, publicId).stream().findFirst();
    }

    private List<DemoRunView.TimelineEntry> timeline(UUID incidentId) {
        return jdbc.query("""
                SELECT entry.sequence_number, entry.entry_type, entry.iteration,
                       entry.content, entry.recorded_at
                FROM agent_transcript_entry entry
                JOIN agent_run run ON run.id = entry.run_id
                WHERE run.id = (
                    SELECT latest.id FROM agent_run latest
                    WHERE latest.incident_id = ?
                    ORDER BY latest.started_at DESC LIMIT 1
                )
                ORDER BY entry.sequence_number
                LIMIT 50
                """, (result, row) -> new DemoRunView.TimelineEntry(
                result.getInt("sequence_number"),
                result.getString("entry_type"),
                result.getInt("iteration"),
                result.getString("content"),
                result.getTimestamp("recorded_at").toInstant()), incidentId);
    }

    private Optional<DemoRunView.RemediationView> remediation(UUID incidentId) {
        return jdbc.query("""
                SELECT request.action_type, runbook.title, request.steps, request.rationale,
                       request.risk_notes, request.grounding_similarity, request.risk_score,
                       request.status, request.decision_note
                FROM remediation_request request
                JOIN runbook ON runbook.id = request.runbook_id
                WHERE request.incident_id = ?
                """, (result, row) -> new DemoRunView.RemediationView(
                result.getString("action_type"),
                result.getString("title"),
                result.getString("steps").lines().toList(),
                result.getString("rationale"),
                result.getString("risk_notes"),
                result.getDouble("grounding_similarity"),
                (Integer) result.getObject("risk_score"),
                result.getString("status"),
                result.getString("decision_note")), incidentId).stream().findFirst();
    }

    private List<DemoRunView.LedgerEntry> ledger(UUID incidentId) {
        return jdbc.query("""
                SELECT event_type, decision, mode, actor, details, recorded_at
                FROM action_ledger
                WHERE incident_id = ?
                ORDER BY recorded_at
                LIMIT 50
                """, (result, row) -> new DemoRunView.LedgerEntry(
                result.getString("event_type"),
                result.getString("decision"),
                result.getString("mode"),
                result.getString("actor"),
                result.getString("details"),
                result.getTimestamp("recorded_at").toInstant()), incidentId);
    }

}
