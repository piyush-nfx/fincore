package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token is invalid, expired or revoked; please log in again");
    }
}
