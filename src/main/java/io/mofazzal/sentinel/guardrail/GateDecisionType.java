package io.mofazzal.sentinel.guardrail;

public enum GateDecisionType {
    ESCALATE,
    REFUSE,
    SKIP,
    DRY_RUN,
    AUTO_EXECUTE,
    REQUIRE_APPROVAL,
    APPROVED_EXECUTE
}
