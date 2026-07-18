package io.mofazzal.sentinel.ledger;

public enum ActionLedgerEventType {
    DECIDED,
    IN_PROGRESS,
    APPLIED,
    FAILED,
    DRY_RUN,
    SKIPPED,
    REFUSED,
    ESCALATED,
    APPROVAL_REQUESTED,
    APPROVED,
    REJECTED,
    COMPENSATION_STARTED,
    COMPENSATED,
    COMPENSATION_FAILED
}
