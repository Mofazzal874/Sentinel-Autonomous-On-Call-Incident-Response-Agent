package io.mofazzal.sentinel.fleet.api;

import io.mofazzal.sentinel.fleet.application.FleetQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fleet")
public class FleetController {

    private final FleetQueryService fleetQueryService;

    public FleetController(FleetQueryService fleetQueryService) {
        this.fleetQueryService = fleetQueryService;
    }

    @GetMapping("/services")
    public List<FleetServiceResponse> listServices() {
        return fleetQueryService.listServices();
    }
}
