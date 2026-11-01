package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

/** A retry of a transfer whose first attempt failed: the client gets the same 422 the first attempt got. */
public class TransferFailedException extends BusinessException {

    public TransferFailedException(String originalReason) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, originalReason);
    }
}
