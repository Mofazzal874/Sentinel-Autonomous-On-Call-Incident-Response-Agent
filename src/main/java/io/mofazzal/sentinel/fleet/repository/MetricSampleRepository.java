package io.mofazzal.sentinel.fleet.repository;

import io.mofazzal.sentinel.fleet.domain.MetricSample;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MetricSampleRepository extends JpaRepository<MetricSample, UUID> {

    @Query("""
            select sample
            from MetricSample sample
            where sample.service.id = :serviceId
              and sample.metricName = :metricName
              and sample.recordedAt between :from and :to
            order by sample.recordedAt desc
            """)
    List<MetricSample> recentWindow(@Param("serviceId") UUID serviceId,
                                    @Param("metricName") String metricName,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable page);
}
