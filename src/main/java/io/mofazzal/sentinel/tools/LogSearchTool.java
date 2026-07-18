package io.mofazzal.sentinel.tools;

import io.mofazzal.sentinel.fleet.domain.LogEvent;
import io.mofazzal.sentinel.fleet.domain.LogLevel;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.LogEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LogSearchTool extends FleetToolSupport {

    private static final Duration MAX_HALF_WINDOW = Duration.ofHours(1);
    private static final int RAW_LIMIT = 100;
    private static final int CLUSTER_LIMIT = 10;
    private final LogEventRepository logs;

    public LogSearchTool(FleetServiceRepository services, LogEventRepository logs) {
        super(services);
        this.logs = logs;
    }

    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN','AGENT')")
    @Transactional(readOnly = true)
    public List<ErrorCluster> errorsAround(String service, Instant at, Duration halfWindow) {
        if (at == null || halfWindow == null || halfWindow.isZero() || halfWindow.isNegative()
                || halfWindow.compareTo(MAX_HALF_WINDOW) > 0) {
            throw new ToolInputException("at is required and halfWindow must be between 1ns and 1h");
        }
        var fleetService = requireService(service);
        List<LogEvent> events = logs.recentWindowByLevel(
                fleetService.getId(), LogLevel.ERROR, at.minus(halfWindow), at.plus(halfWindow),
                PageRequest.of(0, RAW_LIMIT));

        Map<String, List<LogEvent>> grouped = events.stream()
                .collect(Collectors.groupingBy(event -> signature(event.getMessage())));
        return grouped.entrySet().stream()
                .map(entry -> cluster(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(ErrorCluster::count).reversed()
                        .thenComparing(ErrorCluster::lastOccurredAt, Comparator.reverseOrder()))
                .limit(CLUSTER_LIMIT)
                .toList();
    }

    private static ErrorCluster cluster(String signature, List<LogEvent> events) {
        LogEvent latest = events.stream().max(Comparator.comparing(LogEvent::getOccurredAt)).orElseThrow();
        LinkedHashSet<String> traceIds = events.stream()
                .map(LogEvent::getTraceId)
                .filter(id -> id != null && !id.isBlank())
                .limit(5)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ErrorCluster(signature, latest.getMessage(), events.size(),
                events.stream().map(LogEvent::getOccurredAt).min(Instant::compareTo).orElseThrow(),
                latest.getOccurredAt(), new ArrayList<>(traceIds));
    }

    private static String signature(String message) {
        return message.toLowerCase(Locale.ROOT)
                .replaceAll("[0-9a-f]{8,}", "{id}")
                .replaceAll("\\d+", "{n}")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record ErrorCluster(
            String signature,
            String example,
            int count,
            Instant firstOccurredAt,
            Instant lastOccurredAt,
            List<String> traceIds
    ) {
        public ErrorCluster {
            traceIds = List.copyOf(traceIds);
        }
    }
}
