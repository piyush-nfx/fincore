package com.piyush.fincore.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fincore.admin")
public record AdminProperties(String email, String password) {
}
