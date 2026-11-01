package com.piyush.fincore.security;

import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final User alice = user(7L, "alice@example.com", Role.USER);

    @Test
    void tokenRoundTripsUserIdEmailAndRole() {
        JwtService jwt = service(randomSecret(), Duration.ofMinutes(15));

        AuthenticatedUser parsed = jwt.parseAccessToken(jwt.generateAccessToken(alice));

        assertThat(parsed).isEqualTo(new AuthenticatedUser(7L, "alice@example.com", Role.USER));
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service(randomSecret(), Duration.ofMinutes(15));
        String[] parts = jwt.generateAccessToken(alice).split("\\.");

        // Attacker rewrites the payload to claim ADMIN but can't produce a matching signature.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"fincore\",\"sub\":\"7\",\"email\":\"alice@example.com\",\"role\":\"ADMIN\",\"exp\":9999999999}".getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwt.parseAccessToken(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service(randomSecret(), Duration.ofMinutes(15)).generateAccessToken(alice);
        JwtService otherServer = service(randomSecret(), Duration.ofMinutes(15));

        assertThatThrownBy(() -> otherServer.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        // Expired 2 minutes ago: beyond the 30-second clock-skew allowance.
        JwtService jwt = service(randomSecret(), Duration.ofMinutes(-2));

        assertThatThrownBy(() -> jwt.parseAccessToken(jwt.generateAccessToken(alice)))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void secretShorterThan256BitsFailsAtStartup() {
        String weak = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> service(weak, Duration.ofMinutes(15))).isInstanceOf(WeakKeyException.class);
    }

    private static JwtService service(String secret, Duration ttl) {
        return new JwtService(new JwtProperties(secret, ttl, Duration.ofDays(7)));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static User user(Long id, String email, Role role) {
        User user = new User("Test", email, "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
