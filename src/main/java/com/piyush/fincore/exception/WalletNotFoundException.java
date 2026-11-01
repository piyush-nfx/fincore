package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

public class WalletNotFoundException extends BusinessException {

    public WalletNotFoundException(Long walletId) {
        super(HttpStatus.NOT_FOUND, "Wallet " + walletId + " not found");
    }
}
