package io.mofazzal.sentinel.tools;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

final class ToolInputs {

    private static final Pattern SERVICE_NAME = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?");
    private static final Pattern METRIC_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.-]{0,99}");

    private ToolInputs() {
    }

    static String serviceName(String value) {
        if (value == null) {
            throw new ToolInputException("service must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SERVICE_NAME.matcher(normalized).matches()) {
            throw new ToolInputException("service must be 1-100 lowercase letters, digits, or hyphens");
        }
        return normalized;
    }

    static String metricName(String value) {
        if (value == null || !METRIC_NAME.matcher(value.trim()).matches()) {
            throw new ToolInputException("metric must be 1-100 letters, digits, dots, underscores, or hyphens");
        }
        return value.trim();
    }

    static String boundedText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new ToolInputException(field + " must contain 1-" + maxLength + " characters");
        }
        return value.trim();
    }

    static void timeWindow(Instant from, Instant to, Duration maximum) {
        if (from == null || to == null) {
            throw new ToolInputException("from and to must not be null");
        }
        if (!from.isBefore(to)) {
            throw new ToolInputException("from must be before to");
        }
        if (Duration.between(from, to).compareTo(maximum) > 0) {
            throw new ToolInputException("time window must not exceed " + maximum);
        }
    }
}
