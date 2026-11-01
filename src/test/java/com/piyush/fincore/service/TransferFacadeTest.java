package com.piyush.fincore.service;

import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.dto.TransferResult;
import com.piyush.fincore.entity.TransactionStatus;
import com.piyush.fincore.entity.TransactionType;
import com.piyush.fincore.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferFacadeTest {

    private static final TransferRequest REQUEST = new TransferRequest(1L, 2L, BigDecimal.TEN);
    private static final TransferResult RESULT = new TransferResult(new TransactionResponse(
            7L, TransactionType.TRANSFER, TransactionStatus.SUCCESS, 1L, 2L, BigDecimal.TEN, "k", null, Instant.now()), false);

    @Mock
    private TransferService transferService;

    @Test
    void optimisticLockConflictIsRetriedInAFreshCall() {
        when(transferService.transfer(1L, REQUEST, "k"))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L))
                .thenReturn(RESULT);
        TransferFacade facade = new TransferFacade(transferService, new ConcurrencyRetry());

        assertThat(facade.transfer(1L, REQUEST, "k")).isEqualTo(RESULT);
        verify(transferService, times(2)).transfer(1L, REQUEST, "k");
    }

    @Test
    void losingTheSameKeyRaceReturnsTheWinnersTransfer() {
        TransferResult winner = new TransferResult(RESULT.transaction(), true);
        when(transferService.transfer(1L, REQUEST, "k")).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(transferService.findReplay(1L, REQUEST, "k")).thenReturn(Optional.of(winner));
        TransferFacade facade = new TransferFacade(transferService, new ConcurrencyRetry());

        assertThat(facade.transfer(1L, REQUEST, "k")).isEqualTo(winner);
    }

    @Test
    void unrelatedIntegrityErrorIsNotSwallowed() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("something else");
        when(transferService.transfer(1L, REQUEST, "k")).thenThrow(error);
        when(transferService.findReplay(1L, REQUEST, "k")).thenReturn(Optional.empty());
        TransferFacade facade = new TransferFacade(transferService, new ConcurrencyRetry());

        assertThatThrownBy(() -> facade.transfer(1L, REQUEST, "k")).isSameAs(error);
    }
}
