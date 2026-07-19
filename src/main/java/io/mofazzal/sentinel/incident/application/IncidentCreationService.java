package io.mofazzal.sentinel.incident.application;

import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import io.mofazzal.sentinel.observability.SentinelMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class IncidentCreationService {

    private final FleetServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final SentinelMetrics metrics;

    public IncidentCreationService(FleetServiceRepository serviceRepository,
                                   IncidentRepository incidentRepository,
                                   SentinelMetrics metrics) {
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
        this.metrics = metrics;
    }

    @Transactional
    public boolean createIfAbsent(TriageCommand command) {
        String serviceName = command.payload().service().trim().toLowerCase(Locale.ROOT);
        FleetService service = serviceRepository.findByNameAndArchivedAtIsNull(serviceName)
                .orElseThrow(() -> new UnknownFleetServiceException(serviceName));

        int inserted = incidentRepository.insertIfAbsent(
                UUID.randomUUID(),
                command.fingerprint(),
                service.getId(),
                command.payload().severity().name(),
                command.receivedAt()
        );
        if (inserted == 1) {
            metrics.recordIncidentCreated(command.payload().severity().name());
        }
        return inserted == 1;
    }
}
