package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "Email " + email + " is already registered");
    }
}
