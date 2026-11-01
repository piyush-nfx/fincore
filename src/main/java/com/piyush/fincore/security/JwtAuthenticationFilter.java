package com.piyush.fincore.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs once per request, before Spring Security's authorization checks.
 *
 * <p>If the request carries "Authorization: Bearer &lt;token&gt;" and the token is valid, the user is put into the
 * SecurityContext and the request continues as authenticated. Otherwise the request continues anonymously and
 * the authorization rules in SecurityConfig decide: public endpoints work, protected ones get a 401.
 *
 * <p>Deliberately not a @Component: Spring Boot registers every Filter bean with the servlet container too,
 * which would run it a second time outside the security chain. SecurityConfig creates it with "new" instead.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute the entry point reads to explain why a 401 happened. */
    public static final String AUTH_ERROR_ATTRIBUTE = "fincore.authError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            AuthenticatedUser user = jwtService.parseAccessToken(token);
            // Spring Security's convention: roles are authorities prefixed with ROLE_ (hasRole('ADMIN') checks ROLE_ADMIN).
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (ExpiredJwtException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Access token expired, use your refresh token to get a new one");
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid access token");
        }
        chain.doFilter(request, response);
    }
}
