package io.mofazzal.sentinel.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("sentinel.agent")
public record AgentProperties(
        int maxProposalAttempts,
        int maxModelCalls,
        Duration modelCallWindow,
        Duration runRecoveryTimeout
) {
    public AgentProperties {
        if (maxProposalAttempts < 1 || maxProposalAttempts > 3) {
            throw new IllegalArgumentException("sentinel.agent.max-proposal-attempts must be between 1 and 3");
        }
        if (maxModelCalls < 1 || maxModelCalls > 50) {
            throw new IllegalArgumentException("sentinel.agent.max-model-calls must be between 1 and 50");
        }
        if (modelCallWindow == null || modelCallWindow.isZero() || modelCallWindow.isNegative()) {
            throw new IllegalArgumentException("sentinel.agent.model-call-window must be positive");
        }
        if (runRecoveryTimeout == null || runRecoveryTimeout.isZero() || runRecoveryTimeout.isNegative()) {
            throw new IllegalArgumentException("sentinel.agent.run-recovery-timeout must be positive");
        }
    }
}
