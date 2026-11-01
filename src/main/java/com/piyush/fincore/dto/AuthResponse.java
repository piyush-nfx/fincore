package com.piyush.fincore.dto;

/**
 * accessToken goes in the "Authorization: Bearer ..." header of every request.
 * refreshToken is kept by the client and only sent to /api/auth/refresh when the access token expires.
 */
public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
}
