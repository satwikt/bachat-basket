package com.twothreads.bachat.listing;

/** Problems found in one parsed line. Blocking issues stop the line from being saved. */
public enum Issue {
    UNPARSED(true),
    MISSING_PRICE(true),
    ZERO_QUANTITY(true),
    PRICE_ABOVE_ORIGINAL(true),
    ORIGINAL_PRICE_MISSING(true),
    NO_UNIT(false),
    UNKNOWN_ITEM(false);

    private final boolean blocking;

    Issue(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean blocking() {
        return blocking;
    }
}
