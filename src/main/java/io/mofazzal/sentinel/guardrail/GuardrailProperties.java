package io.mofazzal.sentinel.guardrail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

@ConfigurationProperties("sentinel.remediation")
public record GuardrailProperties(
        boolean dryRun,
        int autoExecutionMaxRisk,
        Duration approvalTimeout,
        Duration executionRecoveryTimeout,
        ZoneId businessZone,
        LocalTime peakTrafficStart,
        LocalTime peakTrafficEnd
) {
    public GuardrailProperties {
        if (autoExecutionMaxRisk < 0 || autoExecutionMaxRisk > 100) {
            throw new IllegalArgumentException("autoExecutionMaxRisk must be between 0 and 100");
        }
        if (approvalTimeout == null || approvalTimeout.isNegative() || approvalTimeout.isZero()) {
            throw new IllegalArgumentException("approvalTimeout must be positive");
        }
        if (executionRecoveryTimeout == null
                || executionRecoveryTimeout.isNegative() || executionRecoveryTimeout.isZero()) {
            throw new IllegalArgumentException("executionRecoveryTimeout must be positive");
        }
        if (businessZone == null || peakTrafficStart == null || peakTrafficEnd == null
                || !peakTrafficStart.isBefore(peakTrafficEnd)) {
            throw new IllegalArgumentException("peak traffic window must have a zone and increasing times");
        }
    }
}
