package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** A longer Hindi listing as spoken by the merchant: "पर" between prices, units after prices, "।" between items. */
class VoiceListingHindiMultiTest {

    private static final String TRANSCRIPT =
            "टोमेटोस 10 केजी ओरिजिनल प्राइस 60 पर केजी डिस्काउंटेड प्राइस 35 केजी। "
                    + "बैंगन 5 केजी ओरिजिनल प्राइस 70 पर केजी डिस्काउंटेड प्राइस 35 पर केजी। "
                    + "भिंडी 2 केजी";

    @Test
    void threeItemsAreSplitAtTheSentenceEnd() {
        assertEquals(3, new VoiceListingParser().parse(TRANSCRIPT).size());
    }

    @Test
    void hindiTomatoWithKgAfterEachPrice() {
        ParsedLine tomato = new VoiceListingParser().parse(TRANSCRIPT).get(0);

        assertEquals("TOMATO", tomato.itemKey());
        assertEquals(10, tomato.quantity());
        assertEquals("kg", tomato.unit());
        assertEquals(0, new BigDecimal("35").compareTo(tomato.offerPrice()));
        assertEquals(0, new BigDecimal("60").compareTo(tomato.originalPrice()));
        assertTrue(tomato.issues().isEmpty(), tomato.issues().toString());
    }

    @Test
    void unknownItemIsSavedWithWarningNotBlocked() {
        ParsedLine brinjal = new VoiceListingParser().parse(TRANSCRIPT).get(1);

        assertEquals(null, brinjal.itemKey());
        assertEquals(List.of(Issue.UNKNOWN_ITEM), brinjal.issues());
        assertFalse(brinjal.hasBlockingIssue());
        assertEquals(0, new BigDecimal("70").compareTo(brinjal.originalPrice()));
        assertEquals(0, new BigDecimal("35").compareTo(brinjal.offerPrice()));
    }

    @Test
    void itemWithoutPricesIsNotUnderstood() {
        ParsedLine okra = new VoiceListingParser().parse(TRANSCRIPT).get(2);

        assertEquals(List.of(Issue.UNPARSED), okra.issues());
        assertTrue(okra.hasBlockingIssue());
    }
}
