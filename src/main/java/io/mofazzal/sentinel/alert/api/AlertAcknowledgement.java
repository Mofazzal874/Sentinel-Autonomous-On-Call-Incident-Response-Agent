package io.mofazzal.sentinel.alert.api;

public record AlertAcknowledgement(
        String fingerprint,
        Status status,
        long suppressedCount
) {
    public static AlertAcknowledgement queued(String fingerprint) {
        return new AlertAcknowledgement(fingerprint, Status.QUEUED, 0);
    }

    public static AlertAcknowledgement suppressed(String fingerprint, long suppressedCount) {
        return new AlertAcknowledgement(fingerprint, Status.SUPPRESSED, suppressedCount);
    }

    public enum Status {
        QUEUED,
        SUPPRESSED
    }
}
