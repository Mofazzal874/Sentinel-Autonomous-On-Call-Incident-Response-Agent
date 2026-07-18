package io.mofazzal.sentinel.guardrail;

import java.util.UUID;

public interface DryRunPolicy {
    boolean isEnabled(UUID serviceId);
}
