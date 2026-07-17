package io.mofazzal.sentinel.fleet.application;

import io.mofazzal.sentinel.fleet.api.FleetServiceResponse;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FleetQueryService {

    private final FleetServiceRepository serviceRepository;

    public FleetQueryService(FleetServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public List<FleetServiceResponse> listServices() {
        return serviceRepository.findAllByOrderByNameAsc().stream()
                .map(FleetServiceResponse::from)
                .toList();
    }
}
