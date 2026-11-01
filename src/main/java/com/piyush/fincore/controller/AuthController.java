package com.piyush.fincore.controller;

import com.piyush.fincore.dto.AuthResponse;
import com.piyush.fincore.dto.LoginRequest;
import com.piyush.fincore.dto.RefreshRequest;
import com.piyush.fincore.dto.RegisterRequest;
import com.piyush.fincore.dto.UserResponse;
import com.piyush.fincore.service.AuthService;
import com.piyush.fincore.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// All four endpoints are public (see SecurityConfig): you can't have a token before you log in.
@Tag(name = "Auth", description = "Register, log in, refresh tokens, log out")
@SecurityRequirements // empty = no token needed (shown without a lock in Swagger UI)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
