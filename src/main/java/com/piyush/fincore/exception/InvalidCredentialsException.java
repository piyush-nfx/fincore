package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

/** Same message for "no such email" and "wrong password", so attackers can't discover which emails are registered. */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
