package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;

abstract class FleetToolSupport {

    private final FleetServiceRepository services;

    FleetToolSupport(FleetServiceRepository services) {
        this.services = services;
    }

    protected FleetService requireService(String suppliedName) {
        String name = ToolInputs.serviceName(suppliedName);
        return services.findByName(name)
                .orElseThrow(() -> new ToolInputException("Unknown fleet service: " + name));
    }
}
