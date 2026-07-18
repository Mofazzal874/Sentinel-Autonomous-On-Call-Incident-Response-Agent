package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.Deployment;
import io.mofazzal.sentinel.fleet.domain.DeploymentStatus;
import io.mofazzal.sentinel.fleet.repository.DeploymentRepository;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class DeployQueryTool extends FleetToolSupport {

    private static final int RESULT_LIMIT = 3;
    private final DeploymentRepository deployments;

    public DeployQueryTool(FleetServiceRepository services, DeploymentRepository deployments) {
        super(services);
        this.deployments = deployments;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN','AGENT')")
    @Transactional(readOnly = true)
    public List<DeploySummary> recentDeploys(String service, Instant before) {
        if (before == null) {
            throw new ToolInputException("before must not be null");
        }
        var fleetService = requireService(service);
        return deployments.recentBefore(fleetService.getId(), before, PageRequest.of(0, RESULT_LIMIT))
                .stream()
                .map(DeploySummary::from)
                .toList();
    }

    public record DeploySummary(
            String version,
            String gitSha,
            Instant deployedAt,
            String deployedBy,
            DeploymentStatus status
    ) {
        static DeploySummary from(Deployment deployment) {
            return new DeploySummary(deployment.getVersion(), deployment.getGitSha(),
                    deployment.getDeployedAt(), deployment.getDeployedBy(), deployment.getStatus());
        }
    }
}
