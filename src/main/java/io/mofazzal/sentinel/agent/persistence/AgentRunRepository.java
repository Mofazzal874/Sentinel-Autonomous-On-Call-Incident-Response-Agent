package io.mofazzal.sentinel.agent.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AgentRun run join fetch run.incident where run.id = :runId")
    Optional<AgentRun> findForCompletion(@Param("runId") UUID runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AgentRun run where run.incident.id = :incidentId and run.status = 'RUNNING'")
    Optional<AgentRun> findRunningForUpdate(@Param("incidentId") UUID incidentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select run from AgentRun run join fetch run.incident
            where run.status = 'RUNNING' and run.startedAt < :cutoff
            order by run.startedAt
            """)
    List<AgentRun> findStaleForUpdate(@Param("cutoff") Instant cutoff, Pageable page);
}
