package io.mofazzal.sentinel.ledger;

import io.mofazzal.sentinel.guardrail.GateDecisionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ActionDecisionRecorder {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ActionDecisionRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ActionExecutionRequest request) {
        GateDecisionType type = request.gateDecision().type();
        ActionLedgerEventType event = switch (type) {
            case ESCALATE -> ActionLedgerEventType.ESCALATED;
            case REFUSE -> ActionLedgerEventType.REFUSED;
            case SKIP -> ActionLedgerEventType.SKIPPED;
            case DRY_RUN -> ActionLedgerEventType.DRY_RUN;
            case REQUIRE_APPROVAL -> ActionLedgerEventType.APPROVAL_REQUESTED;
            case AUTO_EXECUTE, APPROVED_EXECUTE -> ActionLedgerEventType.DECIDED;
        };
        ActionLedgerJdbc.append(jdbc, null, request, event, mode(type),
                request.gateDecision().reason(), clock.instant(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void approved(ActionExecutionRequest request, String note) {
        ActionLedgerJdbc.append(jdbc, null, request, ActionLedgerEventType.APPROVED,
                ActionMode.HUMAN_APPROVED, note, clock.instant(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(ActionExecutionRequest request, String note) {
        ActionLedgerJdbc.append(jdbc, null, request, ActionLedgerEventType.REJECTED,
                ActionMode.NONE, note, clock.instant(), null);
    }

    private ActionMode mode(GateDecisionType type) {
        return switch (type) {
            case DRY_RUN -> ActionMode.DRY_RUN;
            case AUTO_EXECUTE -> ActionMode.AUTOMATIC;
            case APPROVED_EXECUTE -> ActionMode.HUMAN_APPROVED;
            default -> ActionMode.NONE;
        };
    }
}
