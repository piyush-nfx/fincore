package com.piyush.fincore.security;

import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Creates and verifies access tokens.
 *
 * <p>A JWT is three Base64 parts: header.payload.signature. The payload (user id, role, expiry) is readable by
 * anyone, so it must never hold secrets. What makes it trustworthy is the signature: an HMAC of header+payload
 * computed with a key only the server knows. Change one character of the payload and the signature no longer
 * matches, so the server rejects it. That's why no session table is needed: the token proves itself.
 */
@Service
public class JwtService {

    private static final String ISSUER = "fincore";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final JwtParser parser;
    private final Duration accessTokenTtl;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set (see .env.example)");
        }
        // Throws WeakKeyException if the key is shorter than 256 bits, so a weak secret fails at startup.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.accessTokenTtl = properties.accessTokenTtl();
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(30) // tolerate small clock differences between servers
                .build();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then returns the user the token was issued to.
     *
     * @throws JwtException if the token is malformed, tampered with, signed with another key, or expired
     */
    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }
}
