package com.piyush.fincore.security;

import com.piyush.fincore.entity.Role;

/**
 * Who is making the current request, as read from a verified access token.
 * Controllers receive it with @AuthenticationPrincipal; services use id() for ownership checks.
 */
public record AuthenticatedUser(Long id, String email, Role role) {
}
