package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.Deployment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    boolean existsByServiceIdAndGitSha(UUID serviceId, String gitSha);

    @Query("""
            select deployment
            from Deployment deployment
            where deployment.service.id = :serviceId
              and deployment.deployedAt <= :at
            order by deployment.deployedAt desc
            """)
    List<Deployment> recentBefore(@Param("serviceId") UUID serviceId,
                                  @Param("at") Instant at,
                                  Pageable page);
}
