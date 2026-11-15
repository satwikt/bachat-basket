package com.twothreads.bachat.dto;

import java.time.LocalTime;

import com.twothreads.bachat.shop.Shop;

/** Read model of a {@link Shop}. Never exposes the entity itself. */
public record ShopDto(
        Long id,
        String name,
        LocalTime closingTime,
        Double latitude,
        Double longitude) {

    public static ShopDto from(Shop shop) {
        return new ShopDto(
                shop.getId(),
                shop.getName(),
                shop.getClosingTime(),
                shop.getLatitude(),
                shop.getLongitude());
    }
}
