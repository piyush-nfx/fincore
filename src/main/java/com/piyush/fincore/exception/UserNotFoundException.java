package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "User " + userId + " not found");
    }
}
