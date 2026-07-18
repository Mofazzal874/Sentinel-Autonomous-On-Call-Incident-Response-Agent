package io.mofazzal.sentinel.incident.api;

import io.mofazzal.sentinel.incident.application.IncidentQueryService;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentQueryController {

    private final IncidentQueryService incidentQueryService;

    public IncidentQueryController(IncidentQueryService incidentQueryService) {
        this.incidentQueryService = incidentQueryService;
    }

    @GetMapping
    public List<IncidentSummary> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return incidentQueryService.list(status, limit);
    }
}
