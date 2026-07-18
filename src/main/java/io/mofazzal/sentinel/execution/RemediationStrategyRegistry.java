package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
class RemediationStrategyRegistry {

    private final Map<RemediationActionType, RemediationStrategy> strategies;

    RemediationStrategyRegistry(List<RemediationStrategy> candidates) {
        Map<RemediationActionType, RemediationStrategy> mapped = new EnumMap<>(RemediationActionType.class);
        for (RemediationStrategy candidate : candidates) {
            if (mapped.put(candidate.actionType(), candidate) != null) {
                throw new IllegalStateException("Duplicate remediation strategy for " + candidate.actionType());
            }
        }
        this.strategies = Map.copyOf(mapped);
    }

    RemediationStrategy require(RemediationActionType actionType) {
        RemediationStrategy strategy = strategies.get(actionType);
        if (strategy == null) {
            throw new IllegalStateException("No remediation strategy registered for " + actionType);
        }
        return strategy;
    }
}
