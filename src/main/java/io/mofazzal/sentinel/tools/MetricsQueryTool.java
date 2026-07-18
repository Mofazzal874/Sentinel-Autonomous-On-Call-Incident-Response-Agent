package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.MetricSample;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.MetricSampleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class MetricsQueryTool extends FleetToolSupport {

    private static final Duration MAX_WINDOW = Duration.ofHours(6);
    private static final int RAW_LIMIT = 360;
    private static final int POINT_LIMIT = 20;
    private final MetricSampleRepository metrics;

    public MetricsQueryTool(FleetServiceRepository services, MetricSampleRepository metrics) {
        super(services);
        this.metrics = metrics;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN','AGENT')")
    @Transactional(readOnly = true)
    public MetricWindow window(String service, String metric, Instant from, Instant to) {
        ToolInputs.timeWindow(from, to, MAX_WINDOW);
        String metricName = ToolInputs.metricName(metric);
        var fleetService = requireService(service);
        List<MetricSample> samples = new ArrayList<>(metrics.recentWindow(
                fleetService.getId(), metricName, from, to, PageRequest.of(0, RAW_LIMIT)));
        Collections.reverse(samples);

        if (samples.isEmpty()) {
            return new MetricWindow(fleetService.getName(), metricName, from, to,
                    List.of(), null, null, null);
        }

        int split = Math.max(1, samples.size() / 2);
        BigDecimal baseline = average(samples.subList(0, split));
        BigDecimal current = samples.size() == 1
                ? samples.getFirst().getValue()
                : average(samples.subList(split, samples.size()));
        BigDecimal percentageDelta = baseline.signum() == 0 ? null
                : current.subtract(baseline)
                .divide(baseline.abs(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return new MetricWindow(fleetService.getName(), metricName, from, to,
                downsample(samples), baseline, current, percentageDelta);
    }

    private static List<MetricPoint> downsample(List<MetricSample> samples) {
        if (samples.size() <= POINT_LIMIT) {
            return samples.stream().map(MetricPoint::from).toList();
        }
        int bucketSize = (int) Math.ceil(samples.size() / (double) POINT_LIMIT);
        List<MetricPoint> points = new ArrayList<>();
        for (int start = 0; start < samples.size(); start += bucketSize) {
            List<MetricSample> bucket = samples.subList(start, Math.min(start + bucketSize, samples.size()));
            points.add(new MetricPoint(bucket.getLast().getRecordedAt(), average(bucket)));
        }
        return List.copyOf(points);
    }

    private static BigDecimal average(List<MetricSample> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        BigDecimal sum = samples.stream()
                .map(MetricSample::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(samples.size()), 6, RoundingMode.HALF_UP);
    }

    public record MetricWindow(
            String service,
            String metric,
            Instant from,
            Instant to,
            List<MetricPoint> points,
            BigDecimal baselineAverage,
            BigDecimal currentAverage,
            BigDecimal percentageDelta
    ) {
        public MetricWindow {
            points = List.copyOf(points);
        }
    }

    public record MetricPoint(Instant recordedAt, BigDecimal value) {
        static MetricPoint from(MetricSample sample) {
            return new MetricPoint(sample.getRecordedAt(), sample.getValue());
        }
    }
}
