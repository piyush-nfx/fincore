package com.piyush.fincore.dto;

import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.User;

import java.time.Instant;

public record UserResponse(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
