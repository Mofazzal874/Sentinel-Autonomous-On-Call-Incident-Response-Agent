package io.mofazzal.sentinel.security.webhook;

import io.mofazzal.sentinel.security.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class WebhookHmacFilter extends OncePerRequestFilter {

    public static final String TIMESTAMP_HEADER = "X-Sentinel-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Sentinel-Signature";

    private final byte[] secret;
    private final long toleranceSeconds;
    private final long maxBodyBytes;
    private final Clock clock;

    public WebhookHmacFilter(SecurityProperties properties, Clock clock) {
        this.secret = properties.webhookSecretBytes();
        this.toleranceSeconds = properties.webhookTolerance().toSeconds();
        this.maxBodyBytes = properties.maxWebhookBodySize().toBytes();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !"/api/v1/alerts".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Webhook body is too large");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(Math.toIntExact(maxBodyBytes + 1));
        if (body.length > maxBodyBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Webhook body is too large");
            return;
        }

        String timestampValue = request.getHeader(TIMESTAMP_HEADER);
        String suppliedSignature = request.getHeader(SIGNATURE_HEADER);
        if (!validTimestamp(timestampValue) || suppliedSignature == null
                || !signatureMatches(timestampValue, body, suppliedSignature)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid webhook signature");
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private boolean validTimestamp(String value) {
        if (value == null) {
            return false;
        }
        try {
            long timestamp = Long.parseLong(value);
            long now = Instant.now(clock).getEpochSecond();
            return timestamp >= now - toleranceSeconds && timestamp <= now + toleranceSeconds;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean signatureMatches(String timestamp, byte[] body, String supplied) {
        String normalized = supplied.startsWith("sha256=") ? supplied.substring(7) : supplied;
        byte[] expected = hmac(timestamp, body);
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(normalized);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] hmac(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return mac.doFinal(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate webhook HMAC", exception);
        }
    }
}
