package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class PersistedServiceActionAllowlist implements ServiceActionAllowlist {

    private final FleetServiceRepository services;

    public PersistedServiceActionAllowlist(FleetServiceRepository services) {
        this.services = services;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean permits(UUID serviceId, RemediationActionType actionType) {
        return services.findWithAllowedActionsById(serviceId)
                .map(service -> service.getAllowedActions().contains(actionType))
                .orElse(false);
    }
}
