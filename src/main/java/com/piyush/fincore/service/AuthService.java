package com.piyush.fincore.service;

import com.piyush.fincore.dto.AuthResponse;
import com.piyush.fincore.dto.LoginRequest;
import com.piyush.fincore.entity.RefreshToken;
import com.piyush.fincore.entity.User;
import com.piyush.fincore.exception.InvalidCredentialsException;
import com.piyush.fincore.exception.InvalidTokenException;
import com.piyush.fincore.repository.RefreshTokenRepository;
import com.piyush.fincore.repository.UserRepository;
import com.piyush.fincore.security.JwtProperties;
import com.piyush.fincore.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTokenTtl;
    // Compared against when the email doesn't exist, so a login for an unknown email takes as long as
    // a wrong password. Otherwise the response time would reveal which emails are registered.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenTtl = jwtProperties.refreshTokenTtl();
        this.dummyPasswordHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                user != null ? user.getPasswordHash() : dummyPasswordHash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /**
     * Refresh token rotation: every refresh token works exactly once. Using it revokes it and returns a new pair.
     *
     * <p>If a token that was already used comes back, someone has a copy of it (an attacker, or the real user
     * after the attacker used it first). We can't tell which, so every session of that user is revoked and both
     * must log in again. noRollbackFor makes sure that revocation is committed even though we throw.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidTokenException::new);
        Instant now = Instant.now();

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), now);
            throw new InvalidTokenException();
        }
        if (stored.isExpired(now)) {
            throw new InvalidTokenException();
        }

        stored.revoke(now);
        return issueTokens(stored.getUser());
    }

    /**
     * Revokes the refresh token so no new access tokens can be made from it. An access token already issued stays
     * valid until it expires (at most 15 minutes): the price of stateless JWTs, kept small by the short lifetime.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        // Unknown tokens are ignored: logout should always "succeed" and never reveal whether a token existed.
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private AuthResponse issueTokens(User user) {
        String rawRefreshToken = newRandomToken();
        refreshTokenRepository.save(new RefreshToken(user, hash(rawRefreshToken), Instant.now().plus(refreshTokenTtl)));
        return new AuthResponse(jwtService.generateAccessToken(user), rawRefreshToken, "Bearer",
                jwtService.getAccessTokenTtl().toSeconds());
    }

    private static String newRandomToken() {
        byte[] bytes = new byte[32]; // 256 bits: impossible to guess
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 (fast) is fine here, unlike for passwords: the input is 256 random bits, so there's nothing to brute-force.
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
