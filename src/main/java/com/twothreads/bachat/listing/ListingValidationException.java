package com.twothreads.bachat.listing;

/** A save request that breaks a listing rule. The message is safe to show the client. */
public class ListingValidationException extends RuntimeException {

    public ListingValidationException(String message) {
        super(message);
    }
}
