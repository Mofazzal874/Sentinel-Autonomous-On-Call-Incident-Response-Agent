package io.mofazzal.sentinel.alert.application;

import io.mofazzal.sentinel.alert.api.AlertPayload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Component
public class AlertFingerprinter {

    public String fingerprint(AlertPayload payload) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, normalize(payload.service()));
        append(canonical, normalize(payload.alertName()));
        payload.labels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    append(canonical, normalize(entry.getKey()));
                    append(canonical, normalize(entry.getValue()));
                });

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('|');
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
