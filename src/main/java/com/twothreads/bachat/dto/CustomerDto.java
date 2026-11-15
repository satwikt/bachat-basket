package com.twothreads.bachat.dto;

import java.time.OffsetDateTime;

import com.twothreads.bachat.customer.entity.Customer;

/** Read model of a {@link Customer}. */
public record CustomerDto(Long id, String name, OffsetDateTime createdAt) {

    public static CustomerDto from(Customer c) {
        return new CustomerDto(c.getId(), c.getName(), c.getCreatedAt());
    }
}
