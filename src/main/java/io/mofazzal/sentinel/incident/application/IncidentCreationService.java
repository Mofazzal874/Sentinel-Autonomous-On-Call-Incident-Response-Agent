package io.mofazzal.sentinel.incident.application;

import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class IncidentCreationService {

    private final FleetServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;

    public IncidentCreationService(FleetServiceRepository serviceRepository,
                                   IncidentRepository incidentRepository) {
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public boolean createIfAbsent(TriageCommand command) {
        String serviceName = command.payload().service().trim().toLowerCase(Locale.ROOT);
        FleetService service = serviceRepository.findByName(serviceName)
                .orElseThrow(() -> new UnknownFleetServiceException(serviceName));

        int inserted = incidentRepository.insertIfAbsent(
                UUID.randomUUID(),
                command.fingerprint(),
                service.getId(),
                command.payload().severity().name(),
                command.receivedAt()
        );
        return inserted == 1;
    }
}
