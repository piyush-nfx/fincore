package com.piyush.fincore.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected, domain-level failures. Each subclass carries its HTTP status,
 * so the global handler maps all of them with a single method.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;

    protected BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
