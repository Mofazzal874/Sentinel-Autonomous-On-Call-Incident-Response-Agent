package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.guardrail.GateDecision;
import io.mofazzal.sentinel.guardrail.RiskBreakdown;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class ActionLedgerJdbc {

    private ActionLedgerJdbc() {
    }

    static void append(JdbcTemplate jdbc,
                       UUID claimId,
                       ActionExecutionRequest request,
                       ActionLedgerEventType eventType,
                       ActionMode mode,
                       String details,
                       Instant recordedAt,
                       UUID compensationOf) {
        GateDecision decision = request.gateDecision();
        RiskBreakdown risk = decision.risk();
        jdbc.update("""
                INSERT INTO action_ledger (
                    id, claim_id, incident_id, fingerprint, action_type, event_type,
                    decision, risk_score, risk_breakdown, mode, actor, details,
                    recorded_at, compensation_of
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), claimId, request.incidentId(), request.fingerprint(),
                request.actionType().name(), eventType.name(), decision.type().name(),
                risk == null ? null : risk.total(), breakdown(risk), mode.name(), request.actor(),
                bounded(details), Timestamp.from(recordedAt), compensationOf);
    }

    private static String breakdown(RiskBreakdown risk) {
        if (risk == null) {
            return null;
        }
        return "actionType=%d,serviceTier=%d,blastRadius=%d,confidencePenalty=%d,businessHours=%d,total=%d"
                .formatted(risk.actionType(), risk.serviceTier(), risk.blastRadius(),
                        risk.confidencePenalty(), risk.businessHours(), risk.total());
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "No details supplied";
        }
        return value.substring(0, Math.min(value.length(), 4_000));
    }
}
