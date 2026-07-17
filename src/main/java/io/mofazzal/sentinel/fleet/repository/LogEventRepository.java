package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.LogEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {

    @Query("""
            select event
            from LogEvent event
            where event.service.id = :serviceId
              and event.occurredAt between :from and :to
            order by event.occurredAt desc
            """)
    List<LogEvent> recentWindow(@Param("serviceId") UUID serviceId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable page);

    @Query("""
            select event
            from LogEvent event
            where event.service.id = :serviceId
              and event.occurredAt between :from and :to
              and lower(event.message) like lower(concat('%', :query, '%'))
            order by event.occurredAt desc
            """)
    List<LogEvent> searchWindow(@Param("serviceId") UUID serviceId,
                                @Param("query") String query,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable page);
}
