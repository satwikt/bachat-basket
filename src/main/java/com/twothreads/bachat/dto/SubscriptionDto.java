package com.twothreads.bachat.dto;

import java.time.OffsetDateTime;

import com.twothreads.bachat.customer.entity.Subscription;

/** Read model of a {@link Subscription} (a customer following a shop). */
public record SubscriptionDto(
        Long id,
        Long customerId,
        Long shopId,
        boolean active,
        OffsetDateTime createdAt) {

    public static SubscriptionDto from(Subscription s) {
        return new SubscriptionDto(
                s.getId(),
                s.getCustomerId(),
                s.getShopId(),
                s.isActive(),
                s.getCreatedAt());
    }
}
