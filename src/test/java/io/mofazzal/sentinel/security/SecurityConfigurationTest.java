package io.mofazzal.sentinel.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.unit.DataSize;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigurationTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final SecurityProperties properties = new SecurityProperties(
            SECRET, "sentinel-test", "sentinel-api", SECRET,
            Duration.ofMinutes(5), DataSize.ofKilobytes(64));
    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void decoderAcceptsCorrectSignatureIssuerAudienceAndTimeWindow() {
        String token = token("sentinel-test", List.of("sentinel-api"),
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));

        Jwt decoded = configuration.jwtDecoder(properties).decode(token);

        assertThat(decoded.getSubject()).isEqualTo("agent-service");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("AGENT");
    }

    @Test
    void decoderRejectsWrongAudience() {
        String token = token("sentinel-test", List.of("another-api"),
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> configuration.jwtDecoder(properties).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsWrongIssuer() {
        String token = token("untrusted-issuer", List.of("sentinel-api"),
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> configuration.jwtDecoder(properties).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsExpiredToken() {
        String token = token("sentinel-test", List.of("sentinel-api"),
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> configuration.jwtDecoder(properties).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void roleConverterUsesRolePrefixAndSubjectPrincipal() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("agent-service")
                .claim("roles", List.of("AGENT", "VIEWER"))
                .build();

        var authentication = configuration.rolesConverter().convert(jwt);

        assertThat(authentication.getName()).isEqualTo("agent-service");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_AGENT", "ROLE_VIEWER", "FACTOR_BEARER");
    }

    private String token(String issuer, List<String> audience, Instant issuedAt, Instant expiresAt) {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("agent-service")
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("AGENT"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
