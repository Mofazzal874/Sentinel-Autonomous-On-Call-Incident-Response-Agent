package io.mofazzal.sentinel.incident.application;

import io.mofazzal.sentinel.incident.api.IncidentSummary;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IncidentQueryService {

    private final IncidentRepository incidents;

    public IncidentQueryService(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN','AGENT')")
    @Transactional(readOnly = true)
    public List<IncidentSummary> list(IncidentStatus status, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        PageRequest page = PageRequest.of(0, limit);
        List<Incident> result = status == null
                ? incidents.findAllByOrderByUpdatedAtDesc(page)
                : incidents.findByStatusOrderByUpdatedAtDesc(status, page);
        return result.stream().map(IncidentSummary::from).toList();
    }
}
