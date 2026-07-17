package io.mofazzal.sentinel.fleet.domain;

public enum RemediationActionType {
    RESTART_SERVICE,
    ROLLBACK_DEPLOYMENT,
    SCALE_OUT,
    CLEAR_CACHE
}
