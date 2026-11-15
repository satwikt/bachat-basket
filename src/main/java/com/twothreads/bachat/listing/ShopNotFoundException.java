package com.twothreads.bachat.listing;

public class ShopNotFoundException extends RuntimeException {

    public ShopNotFoundException(Long shopId) {
        super("shop " + shopId + " not found");
    }
}
