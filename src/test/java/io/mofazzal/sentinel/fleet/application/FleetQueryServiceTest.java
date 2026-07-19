package io.mofazzal.sentinel.fleet.application;

import io.mofazzal.sentinel.fleet.api.FleetServiceResponse;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import io.mofazzal.sentinel.fleet.domain.Team;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FleetQueryServiceTest {

    private final FleetServiceRepository repository = mock(FleetServiceRepository.class);
    private final FleetQueryService queryService = new FleetQueryService(repository);

    @Test
    void mapsFetchedEntitiesToStableDtos() {
        Team team = new Team("Payments", "#team-payments");
        FleetService service = new FleetService(
                "payments-api",
                team,
                ServiceTier.CRITICAL,
                Set.of(RemediationActionType.SCALE_OUT, RemediationActionType.RESTART_SERVICE)
        );
        when(repository.findAllByArchivedAtIsNullOrderByNameAsc()).thenReturn(List.of(service));

        List<FleetServiceResponse> result = queryService.listServices();

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.name()).isEqualTo("payments-api");
            assertThat(response.tier()).isEqualTo(ServiceTier.CRITICAL);
            assertThat(response.owner().name()).isEqualTo("Payments");
            assertThat(response.allowedActions()).containsExactly(
                    RemediationActionType.RESTART_SERVICE,
                    RemediationActionType.SCALE_OUT
            );
        });
        verify(repository).findAllByArchivedAtIsNullOrderByNameAsc();
    }
}
