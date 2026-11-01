package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends BusinessException {

    public IdempotencyKeyConflictException() {
        this("This Idempotency-Key has already been used; generate a new one for a new transfer");
    }

    public IdempotencyKeyConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
