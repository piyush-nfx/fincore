package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

/** A request that is well-formed but breaks a business rule (same-wallet transfer, currency mismatch, bad amount). */
public class InvalidOperationException extends BusinessException {

    public InvalidOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
