package io.mofazzal.sentinel.guardrail;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConfiguredDryRunPolicy implements DryRunPolicy {

    private final GuardrailProperties properties;

    public ConfiguredDryRunPolicy(GuardrailProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isEnabled(UUID serviceId) {
        return properties.dryRun();
    }
}
