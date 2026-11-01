package com.piyush.fincore.controller;

import com.piyush.fincore.dto.RegisterRequest;
import com.piyush.fincore.dto.UserResponse;
import com.piyush.fincore.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Phase 1 has registration only; Phase 2 adds login, JWT and refresh here.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }
}
