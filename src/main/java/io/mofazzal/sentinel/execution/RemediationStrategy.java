package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;

interface RemediationStrategy {
    RemediationActionType actionType();
    ExecutionResult execute(ActionContext context);
    ExecutionResult compensate(ActionContext context);
}
