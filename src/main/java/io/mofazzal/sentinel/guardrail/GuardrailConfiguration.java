package io.mofazzal.sentinel.guardrail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class GuardrailConfiguration {

    @Bean
    GuardrailGate guardrailGate(KillSwitch killSwitch,
                                ServiceActionAllowlist allowlist,
                                DeterministicRiskScorer riskScorer,
                                ActionHistory actionHistory,
                                DryRunPolicy dryRunPolicy,
                                GuardrailProperties properties) {
        return new GuardrailGate(killSwitch, allowlist, riskScorer, actionHistory,
                dryRunPolicy, properties.autoExecutionMaxRisk());
    }
}
