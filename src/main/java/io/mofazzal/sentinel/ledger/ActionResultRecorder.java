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
public class ActionResultRecorder {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ActionResultRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applied(UUID claimId, ActionExecutionRequest request, ActionMode mode,
                        String result, ExecutionAuthorization authorization) {
        requireAuthorization(request, authorization);
        transition(claimId, request, mode, ActionClaimState.IN_PROGRESS, ActionClaimState.APPLIED,
                ActionLedgerEventType.APPLIED, result, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID claimId, ActionExecutionRequest request, ActionMode mode,
                       String result, ExecutionAuthorization authorization) {
        requireAuthorization(request, authorization);
        transition(claimId, request, mode, ActionClaimState.IN_PROGRESS, ActionClaimState.FAILED,
                ActionLedgerEventType.FAILED, result, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensationStarted(UUID claimId, ActionExecutionRequest request, ActionMode mode,
                                    ExecutionAuthorization authorization) {
        requireAuthorization(request, authorization);
        ActionLedgerJdbc.append(jdbc, claimId, request, ActionLedgerEventType.COMPENSATION_STARTED,
                mode, "Compensation started after execution failure", clock.instant(), latestEventId(claimId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensated(UUID claimId, ActionExecutionRequest request, ActionMode mode,
                            String result, ExecutionAuthorization authorization) {
        requireAuthorization(request, authorization);
        transition(claimId, request, mode, ActionClaimState.FAILED, ActionClaimState.COMPENSATED,
                ActionLedgerEventType.COMPENSATED, result, latestEventId(claimId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensationFailed(UUID claimId, ActionExecutionRequest request, ActionMode mode,
                                   String result, ExecutionAuthorization authorization) {
        requireAuthorization(request, authorization);
        transition(claimId, request, mode, ActionClaimState.FAILED, ActionClaimState.COMPENSATION_FAILED,
                ActionLedgerEventType.COMPENSATION_FAILED, result, latestEventId(claimId));
    }

    private void transition(UUID claimId,
                            ActionExecutionRequest request,
                            ActionMode mode,
                            ActionClaimState expected,
                            ActionClaimState next,
                            ActionLedgerEventType event,
                            String result,
                            UUID compensationOf) {
        Instant now = clock.instant();
        int updated = jdbc.update("""
                UPDATE action_claim
                SET state = ?, completed_at = ?, result = ?, version = version + 1
                WHERE id = ? AND state = ?
                """, next.name(), Timestamp.from(now), bounded(result), claimId, expected.name());
        if (updated != 1) {
            throw new IllegalStateException("Action claim is not in expected state " + expected);
        }
        ActionLedgerJdbc.append(jdbc, claimId, request, event, mode, result, now, compensationOf);
    }

    private UUID latestEventId(UUID claimId) {
        return jdbc.queryForObject("""
                SELECT id FROM action_ledger WHERE claim_id = ? ORDER BY recorded_at DESC, id DESC LIMIT 1
                """, UUID.class, claimId);
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "No result supplied";
        }
        return value.substring(0, Math.min(value.length(), 4_000));
    }

    private void requireAuthorization(ActionExecutionRequest request, ExecutionAuthorization authorization) {
        if (authorization == null || !authorization.matches(
                request.incidentId(), request.serviceId(), request.fingerprint(),
                request.actionType(), request.gateDecision().type())) {
            throw new SecurityException("Action result recording requires GuardrailGate authorization");
        }
    }
}
