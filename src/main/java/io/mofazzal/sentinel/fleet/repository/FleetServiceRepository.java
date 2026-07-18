package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.FleetService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FleetServiceRepository extends JpaRepository<FleetService, UUID> {

    Optional<FleetService> findByName(String name);

    @EntityGraph(attributePaths = "allowedActions")
    @Query("select service from FleetService service where service.id = :id")
    Optional<FleetService> findWithAllowedActionsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"ownerTeam", "allowedActions"})
    List<FleetService> findAllByOrderByNameAsc();
}
