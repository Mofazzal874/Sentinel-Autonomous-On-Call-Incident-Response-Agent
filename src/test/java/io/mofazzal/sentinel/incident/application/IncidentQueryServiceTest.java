package io.mofazzal.sentinel.incident.application;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import io.mofazzal.sentinel.fleet.domain.Team;
import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentSeverity;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentQueryServiceTest {

    private final IncidentRepository repository = mock(IncidentRepository.class);
    private final IncidentQueryService service = new IncidentQueryService(repository);

    @Test
    void mapsBoundedOpenIncidentsToStableDtos() {
        FleetService payments = new FleetService(
                "payments-api", new Team("Payments", "#payments"), ServiceTier.CRITICAL, Set.of());
        Instant createdAt = Instant.parse("2026-07-18T10:00:00Z");
        Incident incident = new Incident("fingerprint-1", payments, IncidentSeverity.SEV2, createdAt);
        when(repository.findByStatusOrderByUpdatedAtDesc(
                eq(IncidentStatus.OPEN), argThat((Pageable page) -> page.getPageSize() == 25)))
                .thenReturn(List.of(incident));

        var result = service.list(IncidentStatus.OPEN, 25);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.fingerprint()).isEqualTo("fingerprint-1");
            assertThat(summary.service()).isEqualTo("payments-api");
            assertThat(summary.status()).isEqualTo(IncidentStatus.OPEN);
            assertThat(summary.createdAt()).isEqualTo(createdAt);
        });
        verify(repository, never()).findAllByOrderByUpdatedAtDesc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnboundedLimitBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.list(null, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 100");
        verify(repository, never()).findAllByOrderByUpdatedAtDesc(org.mockito.ArgumentMatchers.any());
    }
}
