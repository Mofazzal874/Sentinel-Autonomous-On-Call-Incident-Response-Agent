package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.Deployment;
import io.mofazzal.sentinel.fleet.domain.DeploymentStatus;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import io.mofazzal.sentinel.fleet.domain.Team;
import io.mofazzal.sentinel.fleet.repository.DeploymentRepository;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployQueryToolTest {

    private final FleetServiceRepository services = mock(FleetServiceRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeployQueryTool tool = new DeployQueryTool(services, deployments);

    @Test
    void returnsTypedNewestFirstDeploymentsWithHardLimit() {
        FleetService service = service();
        Instant before = Instant.parse("2026-07-15T12:05:00Z");
        Deployment bad = new Deployment(service, "2026.07.15.1", "bad-sha",
                before.minusSeconds(300), "release-bot", DeploymentStatus.SUCCEEDED);
        when(services.findByNameAndArchivedAtIsNull("payments-api")).thenReturn(Optional.of(service));
        when(deployments.recentBefore(any(), any(), any())).thenReturn(List.of(bad));

        List<DeployQueryTool.DeploySummary> result = tool.recentDeploys(" Payments-API ", before);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.version()).isEqualTo("2026.07.15.1");
            assertThat(summary.gitSha()).isEqualTo("bad-sha");
        });
        verify(deployments).recentBefore(any(), any(),
                org.mockito.ArgumentMatchers.argThat((Pageable page) -> page.getPageSize() == 3));
    }

    @Test
    void rejectsUnknownServiceWithRecoverableMessage() {
        when(services.findByNameAndArchivedAtIsNull("missing-api")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.recentDeploys("missing-api", Instant.now()))
                .isInstanceOf(ToolInputException.class)
                .hasMessage("Unknown fleet service: missing-api");
    }

    private static FleetService service() {
        return new FleetService("payments-api", new Team("Payments", "#payments"),
                ServiceTier.CRITICAL, Set.of());
    }
}
