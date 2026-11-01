package com.piyush.fincore.service;

import com.piyush.fincore.dto.RegisterRequest;
import com.piyush.fincore.dto.UserResponse;
import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.User;
import com.piyush.fincore.exception.EmailAlreadyExistsException;
import com.piyush.fincore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        // Friendly early check; the UNIQUE index still catches two simultaneous sign-ups (-> 409 in the handler).
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.USER);
        return UserResponse.from(userRepository.save(user));
    }
}
