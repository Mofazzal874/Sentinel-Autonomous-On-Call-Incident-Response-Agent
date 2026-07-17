package io.mofazzal.sentinel.fleet.api;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;

import java.util.List;
import java.util.UUID;

public record FleetServiceResponse(
        UUID id,
        String name,
        ServiceTier tier,
        TeamResponse owner,
        List<RemediationActionType> allowedActions
) {
    public static FleetServiceResponse from(FleetService service) {
        List<RemediationActionType> actions = service.getAllowedActions().stream()
                .sorted()
                .toList();
        return new FleetServiceResponse(
                service.getId(),
                service.getName(),
                service.getTier(),
                new TeamResponse(
                        service.getOwnerTeam().getId(),
                        service.getOwnerTeam().getName(),
                        service.getOwnerTeam().getContactChannel()),
                actions
        );
    }

    public record TeamResponse(UUID id, String name, String contactChannel) {
    }
}
