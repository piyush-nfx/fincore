package com.piyush.fincore.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** A stable JSON shape for paged results (Spring's own Page object is not meant to be serialised directly). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
