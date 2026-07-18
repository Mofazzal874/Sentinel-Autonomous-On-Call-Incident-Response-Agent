package io.mofazzal.sentinel.agent.application;

import java.util.UUID;

public interface ModelCallBudget {
    void acquire(UUID incidentId, String role);
}
