package io.mofazzal.sentinel.incident.domain;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import io.mofazzal.sentinel.fleet.domain.Team;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void followsAnAllowedLifecycle() {
        Incident incident = incident();

        incident.transitionTo(IncidentStatus.TRIAGING, CREATED_AT.plus(1, ChronoUnit.MINUTES));
        incident.transitionTo(IncidentStatus.AWAITING_APPROVAL, CREATED_AT.plus(2, ChronoUnit.MINUTES));
        incident.transitionTo(IncidentStatus.REMEDIATING, CREATED_AT.plus(3, ChronoUnit.MINUTES));
        incident.transitionTo(IncidentStatus.RESOLVED, CREATED_AT.plus(4, ChronoUnit.MINUTES));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getUpdatedAt()).isEqualTo(CREATED_AT.plus(4, ChronoUnit.MINUTES));
    }

    @Test
    void rejectsSkippingDirectlyFromOpenToResolved() {
        Incident incident = incident();

        assertThatThrownBy(() -> incident.transitionTo(
                IncidentStatus.RESOLVED, CREATED_AT.plus(1, ChronoUnit.MINUTES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPEN -> RESOLVED");
    }

    private static Incident incident() {
        Team team = new Team("Payments", "#team-payments");
        FleetService service = new FleetService("payments-api", team, ServiceTier.CRITICAL, Set.of());
        return new Incident("payments-api:error-rate", service, IncidentSeverity.SEV2, CREATED_AT);
    }
}
