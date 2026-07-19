package io.mofazzal.sentinel.fleet.api;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.demo.ScenarioTemplate;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.fleet.domain.ServiceDependency;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CatalogContracts {

    private CatalogContracts() {
    }

    public record TeamWrite(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 200) String contactChannel,
            @PositiveOrZero Long version) {
    }

    public record TeamView(UUID id, String name, String contactChannel,
                           Instant archivedAt, long version) {
    }

    public record ServiceWrite(
            @NotBlank @Size(max = 100) String name,
            @NotNull UUID ownerTeamId,
            @NotNull ServiceTier tier,
            @NotNull Set<RemediationActionType> allowedActions,
            @PositiveOrZero Long version) {
    }

    public record ServiceView(UUID id, String name, UUID ownerTeamId, String ownerTeamName,
                              ServiceTier tier, Set<RemediationActionType> allowedActions,
                              Instant archivedAt, long version) {
    }

    public record DependencyCreate(
            @NotNull UUID callerServiceId,
            @NotNull UUID dependencyServiceId,
            @NotNull ServiceDependency.Criticality criticality) {
    }

    public record DependencyView(UUID id, UUID callerServiceId, String callerServiceName,
                                 UUID dependencyServiceId, String dependencyServiceName,
                                 ServiceDependency.Criticality criticality,
                                 Instant createdAt, long version) {
    }

    public record RunbookWrite(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 4000) String symptomDescription,
            @NotBlank @Size(max = 8000) String steps,
            @NotNull RemediationActionType actionType,
            @PositiveOrZero Long version) {
    }

    public record RunbookView(UUID id, String title, String symptomDescription, String steps,
                              RemediationActionType actionType, Instant archivedAt, long version) {
    }

    public record ScenarioWrite(
            @NotBlank @Size(max = 80) String scenarioKey,
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Size(max = 500) String description,
            @NotNull ScenarioTemplate.ScenarioType scenarioType,
            @NotNull UUID serviceId,
            @NotNull IncidentSeverity severity,
            boolean enabled,
            @PositiveOrZero Long version) {
    }

    public record ScenarioView(UUID id, String scenarioKey, String displayName, String description,
                               ScenarioTemplate.ScenarioType scenarioType, UUID serviceId,
                               String serviceName, IncidentSeverity severity, boolean enabled,
                               Instant createdAt, Instant updatedAt, Instant archivedAt, long version) {
    }

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    public record Problem(String code, String message) {
    }
}
