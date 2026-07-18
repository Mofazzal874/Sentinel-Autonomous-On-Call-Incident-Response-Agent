package io.mofazzal.sentinel.ledger;

public enum ActionClaimState {
    IN_PROGRESS,
    APPLIED,
    FAILED,
    COMPENSATED,
    COMPENSATION_FAILED
}
