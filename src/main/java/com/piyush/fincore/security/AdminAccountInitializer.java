package com.piyush.fincore.security;

import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.User;
import com.piyush.fincore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the ADMIN account at startup from ADMIN_EMAIL / ADMIN_PASSWORD.
 * Admins can't sign up through the API (register always creates USER), so this is the only way in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = adminProperties.email();
        String password = adminProperties.password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("ADMIN_EMAIL / ADMIN_PASSWORD not set; no admin account created");
            return;
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be 8-72 characters");
        }
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            return; // created on an earlier start; never overwrite it
        }
        userRepository.save(new User("Administrator", normalized, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created admin account {}", normalized);
    }
}
