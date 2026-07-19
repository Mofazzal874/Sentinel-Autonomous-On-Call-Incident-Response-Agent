package io.mofazzal.sentinel.demo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScenarioTemplateRepository extends JpaRepository<ScenarioTemplate, UUID> {
    @EntityGraph(attributePaths = "service")
    Page<ScenarioTemplate> findByArchivedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = "service")
    Page<ScenarioTemplate> findAllBy(Pageable pageable);
}
