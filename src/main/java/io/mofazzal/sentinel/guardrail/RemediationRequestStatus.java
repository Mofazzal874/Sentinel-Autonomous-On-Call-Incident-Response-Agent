package io.mofazzal.sentinel.guardrail;

public enum RemediationRequestStatus {
    PENDING_DECISION,
    AWAITING_APPROVAL,
    EXECUTING,
    COMPLETED,
    DRY_RUN,
    REFUSED,
    ESCALATED,
    SKIPPED,
    REJECTED
}
