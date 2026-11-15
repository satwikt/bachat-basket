package com.twothreads.bachat.dto;

/** A shop a customer follows: the id and the name, for the subscriptions list. */
public record FollowedShopDto(Long shopId, String shopName) {
}
