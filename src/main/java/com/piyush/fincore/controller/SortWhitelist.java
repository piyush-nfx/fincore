package com.piyush.fincore.controller;

import com.piyush.fincore.exception.InvalidOperationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Spring turns "?sort=anything" into ORDER BY anything. Sorting by a field the client can't see is a sneaky leak
 * (e.g. sort by a hidden column and watch how the order changes), and an unknown field would surface as a 500.
 * So list endpoints only accept the sort fields named here.
 */
final class SortWhitelist {

    static final Set<String> TRANSACTION_FIELDS = Set.of("createdAt", "amount", "id");

    private SortWhitelist() {
    }

    static Pageable check(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidOperationException(
                        "Cannot sort by '" + order.getProperty() + "'; allowed: " + String.join(", ", allowed));
            }
        }
        return pageable;
    }
}
