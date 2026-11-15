package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Hindi wording as spoken by the merchant (transcript in Devanagari). */
class VoiceListingHindiTest {

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
    void hindiTranscriptFromTheMerchantParses() {
        assertMilk("दूध 6 पैकेट ओरिजिनल प्राइस 30 रुपीस, डिस्काउंटेड प्राइस 23।");
    }

    @Test
    void hindiWordingWithCommaBeforeOriginal() {
        assertMilk("दूध 6 पैकेट, ओरिजिनल प्राइस 30 रुपये, डिस्काउंट प्राइस 23");
    }

    @Test
    void hindiMoolyaAndChhootWords() {
        assertMilk("दूध 6 पैकेट मूल्य 30 छूट 23");
    }

    @Test
    void hindiUnitsAndItems() {
        ParsedLine tomato = parser.parse("टमाटर 5 किलो, ओरिजिनल प्राइस 30 डिस्काउंटेड प्राइस 15").get(0);
        assertEquals("TOMATO", tomato.itemKey());
        assertEquals("kg", tomato.unit());
        assertEquals(0, new BigDecimal("15").compareTo(tomato.offerPrice()));
    }
}
