package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Gujarati wording as spoken by the merchant (transcript in Gujarati script). */
class VoiceListingGujaratiTest {

    private final VoiceListingParser parser = new VoiceListingParser();

    private void assertMilk(String transcript) {
        List<ParsedLine> lines = parser.parse(transcript);
        assertEquals(1, lines.size(), transcript);
        ParsedLine line = lines.get(0);
        assertEquals("MILK", line.itemKey(), transcript);
        assertEquals(6, line.quantity(), transcript);
        assertEquals("packet", line.unit(), transcript);
        assertEquals(0, new BigDecimal("23").compareTo(line.offerPrice()), transcript);
        assertEquals(0, new BigDecimal("30").compareTo(line.originalPrice()), transcript);
        assertTrue(line.issues().isEmpty(), transcript + " -> " + line.issues());
    }

    @Test
    void gujaratiTranscriptFromTheMerchantParses() {
        assertMilk("દૂધ 6 પેકેટ, ઓરિજનલ પ્રાઈસ ₹30, ડિસ્કાઉન્ટેડ પ્રાઈસ ₹23.");
    }

    @Test
    void gujaratiWordingWithoutPriceWord() {
        assertMilk("દૂધ 6 પેકેટ, ઓરિજનલ 30 ડિસ્કાઉન્ટેડ 23");
    }

    @Test
    void gujaratiSpellingVariantsOfOriginal() {
        assertMilk("દૂધ 6 પેકેટ, ઓરીજનલ પ્રાઈસ 30 ડિસ્કાઉન્ટેડ પ્રાઈસ 23");
    }

    @Test
    void gujaratiItemNamesAreRecognised() {
        ParsedLine tomato = parser.parse("ટામેટા 5 કિલો, ઓરિજનલ પ્રાઈસ 30 ડિસ્કાઉન્ટેડ પ્રાઈસ 15").get(0);
        assertEquals("TOMATO", tomato.itemKey());
        assertEquals("kg", tomato.unit());

        ParsedLine onion = parser.parse("ડુંગળી 2 કિલો, ઓરિજનલ પ્રાઈસ 40 ડિસ્કાઉન્ટેડ પ્રાઈસ 30").get(0);
        assertEquals("ONION", onion.itemKey());
    }
}
