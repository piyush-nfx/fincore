package com.piyush.fincore.service;

import com.piyush.fincore.dto.AuthResponse;
import com.piyush.fincore.dto.LoginRequest;
import com.piyush.fincore.entity.RefreshToken;
import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.User;
import com.piyush.fincore.exception.InvalidCredentialsException;
import com.piyush.fincore.exception.InvalidTokenException;
import com.piyush.fincore.repository.RefreshTokenRepository;
import com.piyush.fincore.repository.UserRepository;
import com.piyush.fincore.security.JwtProperties;
import com.piyush.fincore.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;
    private User alice;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(Base64.getEncoder().encodeToString(new byte[64]),
                Duration.ofMinutes(15), Duration.ofDays(7));
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, new JwtService(props), props);

        alice = new User("Alice", "alice@example.com", "alice-hash", Role.USER);
        ReflectionTestUtils.setField(alice, "id", 1L);
    }

    @Test
    void loginWithCorrectPasswordReturnsTokensAndStoresOnlyTheRefreshTokenHash() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("correct-pass", "alice-hash")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest(" Alice@Example.com ", "correct-pass"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(AuthService.hash(response.refreshToken()))
                .isNotEqualTo(response.refreshToken());
    }

    @Test
    void wrongPasswordIsRejected() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("wrong", "alice-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unknownEmailGetsTheSameErrorAndStillRunsBcrypt() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        // Comparing against the dummy hash keeps the response time the same as for a wrong password.
        verify(passwordEncoder).matches("whatever", "dummy-hash");
    }

    @Test
    void refreshRotatesTheTokenAndIssuesANewPair() {
        RefreshToken stored = new RefreshToken(alice, AuthService.hash("old-token"), Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("old-token"))).thenReturn(Optional.of(stored));

        AuthResponse response = authService.refresh("old-token");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isNotEqualTo("old-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void reusingARotatedTokenRevokesEverySessionOfThatUser() {
        RefreshToken alreadyUsed = new RefreshToken(alice, AuthService.hash("stolen"), Instant.now().plusSeconds(3600));
        alreadyUsed.revoke(Instant.now());
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("stolen"))).thenReturn(Optional.of(alreadyUsed));

        assertThatThrownBy(() -> authService.refresh("stolen")).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        RefreshToken expired = new RefreshToken(alice, AuthService.hash("old"), Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("old")).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("made-up")).isInstanceOf(InvalidTokenException.class);
    }
}
