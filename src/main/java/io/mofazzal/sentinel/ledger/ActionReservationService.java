package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.guardrail.ExecutionAuthorization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ActionReservationService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ActionReservationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActionReservation reserve(ActionExecutionRequest request,
                                     ActionMode mode,
                                     ExecutionAuthorization authorization) {
        requireAuthorization(request, authorization);
        UUID claimId = UUID.randomUUID();
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO action_claim (
                    id, incident_id, fingerprint, action_type, state, created_at, version
                ) VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, 0)
                ON CONFLICT (fingerprint, action_type) DO NOTHING
                """, claimId, request.incidentId(), request.fingerprint(), request.actionType().name(),
                Timestamp.from(now));
        if (inserted == 0) {
            UUID existingId = jdbc.queryForObject("""
                    SELECT id FROM action_claim WHERE fingerprint = ? AND action_type = ?
                    """, UUID.class, request.fingerprint(), request.actionType().name());
            return new ActionReservation(false, existingId);
        }
        ActionLedgerJdbc.append(jdbc, claimId, request, ActionLedgerEventType.IN_PROGRESS,
                mode, "Execution reservation committed before side effect", now, null);
        return new ActionReservation(true, claimId);
    }

    private void requireAuthorization(ActionExecutionRequest request, ExecutionAuthorization authorization) {
        if (authorization == null || !authorization.matches(
                request.incidentId(), request.serviceId(), request.fingerprint(),
                request.actionType(), request.gateDecision().type())) {
            throw new SecurityException("Action reservation requires GuardrailGate authorization");
        }
    }
}
