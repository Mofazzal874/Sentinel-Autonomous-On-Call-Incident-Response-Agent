package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.guardrail.GateDecisionType;
import io.mofazzal.sentinel.guardrail.ExecutionAuthorization;
import io.mofazzal.sentinel.ledger.ActionExecutionRequest;
import io.mofazzal.sentinel.ledger.ActionMode;
import io.mofazzal.sentinel.ledger.ActionReservation;
import io.mofazzal.sentinel.ledger.ActionReservationService;
import io.mofazzal.sentinel.ledger.ActionResultRecorder;
import org.springframework.stereotype.Service;

@Service
public class RemediationExecutor {

    private final ActionReservationService reservations;
    private final ActionResultRecorder results;
    private final RemediationStrategyRegistry strategies;

    public RemediationExecutor(ActionReservationService reservations,
                               ActionResultRecorder results,
                               RemediationStrategyRegistry strategies) {
        this.reservations = reservations;
        this.results = results;
        this.strategies = strategies;
    }

    public ExecutionOutcome execute(ActionExecutionRequest request, ExecutionAuthorization authorization) {
        if (authorization == null || !authorization.matches(
                request.incidentId(), request.serviceId(), request.fingerprint(),
                request.actionType(), request.gateDecision().type())) {
            throw new SecurityException("Execution requires a matching GuardrailGate authorization");
        }
        ActionMode mode = executionMode(request.gateDecision().type());
        ActionReservation reservation = reservations.reserve(request, mode, authorization);
        if (!reservation.acquired()) {
            return new ExecutionOutcome(false, reservation.claimId(), "Equivalent action already reserved");
        }

        ActionContext context = new ActionContext(reservation.claimId(), request.incidentId(),
                request.serviceId(), request.actionType());
        RemediationStrategy strategy = strategies.require(request.actionType());
        try {
            ExecutionResult execution = strategy.execute(context);
            results.applied(reservation.claimId(), request, mode, execution.details(), authorization);
            return new ExecutionOutcome(true, reservation.claimId(), execution.details());
        } catch (RuntimeException executionFailure) {
            results.failed(reservation.claimId(), request, mode, failureMessage(executionFailure), authorization);
            compensate(strategy, context, reservation, request, mode, authorization);
            throw executionFailure;
        }
    }

    private void compensate(RemediationStrategy strategy,
                            ActionContext context,
                            ActionReservation reservation,
                            ActionExecutionRequest request,
                            ActionMode mode,
                            ExecutionAuthorization authorization) {
        results.compensationStarted(reservation.claimId(), request, mode, authorization);
        try {
            ExecutionResult compensation = strategy.compensate(context);
            results.compensated(reservation.claimId(), request, mode, compensation.details(), authorization);
        } catch (RuntimeException compensationFailure) {
            results.compensationFailed(reservation.claimId(), request, mode,
                    failureMessage(compensationFailure), authorization);
        }
    }

    private ActionMode executionMode(GateDecisionType decision) {
        return switch (decision) {
            case AUTO_EXECUTE -> ActionMode.AUTOMATIC;
            case APPROVED_EXECUTE -> ActionMode.HUMAN_APPROVED;
            default -> throw new IllegalArgumentException("Gate decision does not authorize execution: " + decision);
        };
    }

    private String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public record ExecutionOutcome(boolean applied, java.util.UUID claimId, String details) {
    }
}
