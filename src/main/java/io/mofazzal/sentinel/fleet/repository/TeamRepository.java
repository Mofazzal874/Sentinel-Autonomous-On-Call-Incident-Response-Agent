package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Page<Team> findByArchivedAtIsNull(Pageable pageable);
}
