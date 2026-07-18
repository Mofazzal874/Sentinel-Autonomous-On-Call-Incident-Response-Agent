package io.mofazzal.sentinel.incident.repository;

import io.mofazzal.sentinel.incident.domain.Incident;
import io.mofazzal.sentinel.incident.domain.IncidentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @EntityGraph(attributePaths = "service")
    List<Incident> findAllByOrderByUpdatedAtDesc(Pageable page);

    @EntityGraph(attributePaths = "service")
    List<Incident> findByStatusOrderByUpdatedAtDesc(IncidentStatus status, Pageable page);

    Optional<Incident> findByFingerprint(String fingerprint);

    long countByFingerprint(String fingerprint);

    @Modifying
    @Query(value = """
            INSERT INTO incident (
                id, fingerprint, service_id, status, severity,
                created_at, updated_at, version
            ) VALUES (
                :id, :fingerprint, :serviceId, 'OPEN', :severity,
                :createdAt, :createdAt, 0
            )
            ON CONFLICT (fingerprint) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("fingerprint") String fingerprint,
                       @Param("serviceId") UUID serviceId,
                       @Param("severity") String severity,
                       @Param("createdAt") Instant createdAt);
}
