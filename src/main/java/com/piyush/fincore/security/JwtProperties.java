package com.piyush.fincore.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Typed view of the fincore.jwt.* settings (a typo in a property name fails at startup, not at runtime). */
@ConfigurationProperties(prefix = "fincore.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
