package com.piyush.fincore.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(Long walletId, BigDecimal balance, BigDecimal requested) {
        super(HttpStatus.UNPROCESSABLE_CONTENT,
                "Insufficient balance in wallet " + walletId + ": available "
                        + balance.toPlainString() + ", requested " + requested.toPlainString());
    }
}
