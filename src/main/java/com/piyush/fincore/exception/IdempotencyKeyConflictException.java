package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends BusinessException {

    public IdempotencyKeyConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
