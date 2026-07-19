package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.ServiceDependency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceDependencyRepository extends JpaRepository<ServiceDependency, UUID> {

    @EntityGraph(attributePaths = {"callerService", "dependencyService"})
    Page<ServiceDependency> findAllBy(Pageable pageable);

    boolean existsByCallerServiceIdAndDependencyServiceId(UUID callerServiceId, UUID dependencyServiceId);
}
