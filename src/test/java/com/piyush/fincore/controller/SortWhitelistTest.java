package com.piyush.fincore.controller;

import com.piyush.fincore.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortWhitelistTest {

    @Test
    void allowedSortFieldsPassThrough() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending().and(Sort.by("amount")));

        assertThat(SortWhitelist.check(pageable, SortWhitelist.TRANSACTION_FIELDS)).isSameAs(pageable);
    }

    @Test
    void hiddenFieldsCannotBeUsedForSorting() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("fromWallet.user.passwordHash"));

        assertThatThrownBy(() -> SortWhitelist.check(pageable, SortWhitelist.TRANSACTION_FIELDS))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot sort by 'fromWallet.user.passwordHash'");
    }
}
