package io.mofazzal.sentinel.security.webhook;

import io.mofazzal.sentinel.security.SecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WebhookHmacFilterTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final Instant NOW = Instant.parse("2026-07-18T06:00:00Z");
    private final WebhookHmacFilter filter = new WebhookHmacFilter(
            new SecurityProperties(SECRET, "sentinel-test", "sentinel-api", SECRET,
                    Duration.ofMinutes(5), DataSize.ofBytes(128)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsCurrentValidSignatureAndPreservesBodyForController() throws Exception {
        byte[] body = "{\"service\":\"payments-api\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        MockHttpServletRequest request = request(body, timestamp, signature(timestamp, body));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(CachedBodyRequest.class), any(MockHttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingSignature() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request(body, Long.toString(NOW.getEpochSecond()), null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsStaleTimestampEvenWithMatchingSignature() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        MockHttpServletRequest request = request(body, timestamp, signature(timestamp, body));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsSignatureForDifferentBody() throws Exception {
        byte[] body = "{\"actual\":true}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        String wrong = signature(timestamp, "{\"actual\":false}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = request(body, timestamp, wrong);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsOversizedBodyBeforeHmacWork() throws Exception {
        byte[] body = new byte[129];
        MockHttpServletRequest request = request(body, Long.toString(NOW.getEpochSecond()), "00");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    private static MockHttpServletRequest request(byte[] body, String timestamp, String signature) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/alerts");
        request.setContent(body);
        request.addHeader(WebhookHmacFilter.TIMESTAMP_HEADER, timestamp);
        if (signature != null) {
            request.addHeader(WebhookHmacFilter.SIGNATURE_HEADER, signature);
        }
        return request;
    }

    private static String signature(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
