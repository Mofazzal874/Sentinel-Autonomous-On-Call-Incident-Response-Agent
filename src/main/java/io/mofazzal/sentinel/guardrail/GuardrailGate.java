package io.mofazzal.sentinel.guardrail;

import java.util.Objects;

public class GuardrailGate {

    private final KillSwitch killSwitch;
    private final ServiceActionAllowlist allowlist;
    private final DeterministicRiskScorer riskScorer;
    private final ActionHistory actionHistory;
    private final DryRunPolicy dryRunPolicy;
    private final int autoExecutionMaxRisk;

    public GuardrailGate(KillSwitch killSwitch,
                         ServiceActionAllowlist allowlist,
                         DeterministicRiskScorer riskScorer,
                         ActionHistory actionHistory,
                         DryRunPolicy dryRunPolicy,
                         int autoExecutionMaxRisk) {
        this.killSwitch = Objects.requireNonNull(killSwitch, "killSwitch");
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.riskScorer = Objects.requireNonNull(riskScorer, "riskScorer");
        this.actionHistory = Objects.requireNonNull(actionHistory, "actionHistory");
        this.dryRunPolicy = Objects.requireNonNull(dryRunPolicy, "dryRunPolicy");
        if (autoExecutionMaxRisk < 0) {
            throw new IllegalArgumentException("autoExecutionMaxRisk must not be negative");
        }
        this.autoExecutionMaxRisk = autoExecutionMaxRisk;
    }

    public GateDecision decide(GateRequest request) {
        return evaluate(request).decision();
    }

    GateEvaluation evaluate(GateRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            if (killSwitch.isEngaged()) {
                return denied(GateDecisionType.ESCALATE, "kill switch engaged", null);
            }
        } catch (RuntimeException unavailable) {
            return denied(GateDecisionType.ESCALATE, "kill switch state unavailable", null);
        }

        if (!allowlist.permits(request.serviceId(), request.riskFacts().actionType())) {
            return denied(GateDecisionType.REFUSE, "action is not allowlisted for service", null);
        }

        RiskBreakdown risk = riskScorer.score(request.riskFacts());

        if (actionHistory.alreadyActiveOrApplied(
                request.incidentFingerprint(), request.riskFacts().actionType())) {
            return denied(GateDecisionType.SKIP, "equivalent action is active or already applied", risk);
        }

        if (dryRunPolicy.isEnabled(request.serviceId())) {
            return denied(GateDecisionType.DRY_RUN, "dry-run mode is enabled", risk);
        }

        if (risk.total() <= autoExecutionMaxRisk) {
            return authorized(request, GateDecisionType.AUTO_EXECUTE,
                    "risk is within automatic threshold", risk);
        }

        if (request.approvedByHuman()) {
            return authorized(request, GateDecisionType.APPROVED_EXECUTE,
                    "human approval accepted after gate re-check", risk);
        }

        return denied(GateDecisionType.REQUIRE_APPROVAL, "risk exceeds automatic threshold", risk);
    }

    private GateEvaluation denied(GateDecisionType type, String reason, RiskBreakdown risk) {
        return new GateEvaluation(new GateDecision(type, reason, risk), null);
    }

    private GateEvaluation authorized(GateRequest request,
                                      GateDecisionType type,
                                      String reason,
                                      RiskBreakdown risk) {
        return new GateEvaluation(new GateDecision(type, reason, risk),
                new ExecutionAuthorization(request, type));
    }
}
