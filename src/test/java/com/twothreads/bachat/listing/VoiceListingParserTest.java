package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class VoiceListingParserTest {

    private final VoiceListingParser parser = new VoiceListingParser();

    private ParsedLine one(String transcript) {
        List<ParsedLine> lines = parser.parse(transcript);
        assertEquals(1, lines.size(), "expected one line for: " + transcript);
        return lines.get(0);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertTrue(actual != null && new BigDecimal(expected).compareTo(actual) == 0,
                "expected " + expected + " but was " + actual);
    }

    // ---- Worked examples (spec section 3) ----

    @Test
    void milkWithBothPrices() {
        ParsedLine line = one("doodh 6 packet, original price 30 discounted price 23");
        assertEquals("MILK", line.itemKey());
        assertEquals("Milk", line.itemName());
        assertEquals(6, line.quantity());
        assertEquals("packet", line.unit());
        assertMoney("23", line.offerPrice());
        assertMoney("30", line.originalPrice());
        assertTrue(line.issues().isEmpty());
        assertEquals("doodh 6 packet original price 30 discounted price 23", line.rawPhrase());
    }

    @Test
    void breadWithBothPrices() {
        ParsedLine line = one("bread 4 loaf, original price 35 discounted price 30");
        assertEquals("BREAD", line.itemKey());
        assertEquals(4, line.quantity());
        assertEquals("loaf", line.unit());
        assertMoney("30", line.offerPrice());
        assertMoney("35", line.originalPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void tomatoWithBothPrices() {
        ParsedLine line = one("tamatar 5 kg, original price 30 discounted price 15");
        assertEquals("TOMATO", line.itemKey());
        assertEquals(5, line.quantity());
        assertEquals("kg", line.unit());
        assertMoney("15", line.offerPrice());
        assertMoney("30", line.originalPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void missingUnitDefaultsToPcWithWarning() {
        ParsedLine line = one("doodh 6, original price 30 discounted price 23");
        assertEquals("pc", line.unit());
        assertEquals(List.of(Issue.NO_UNIT), line.issues());
        assertFalse(line.hasBlockingIssue());
    }

    @Test
    void offerEqualToOriginalIsBlocked() {
        ParsedLine line = one("doodh 6 packet, original price 30 discounted price 30");
        assertEquals(List.of(Issue.PRICE_ABOVE_ORIGINAL), line.issues());
        assertTrue(line.hasBlockingIssue());
    }

    @Test
    void offerAboveOriginalIsBlocked() {
        ParsedLine line = one("doodh 6 packet, original price 30 discounted price 40");
        assertEquals(List.of(Issue.PRICE_ABOVE_ORIGINAL), line.issues());
    }

    @Test
    void discountedPriceWithoutOriginalIsUnparsed() {
        ParsedLine line = one("doodh 6 packet, discounted price 23");
        assertEquals(List.of(Issue.UNPARSED), line.issues());
        assertNull(line.itemKey());
        assertNull(line.quantity());
        assertNull(line.offerPrice());
        assertNull(line.originalPrice());
        assertEquals("doodh 6 packet discounted price 23", line.rawPhrase());
    }

    @Test
    void oldShortFormIsNotAccepted() {
        ParsedLine line = one("doodh 6 packet 22");
        assertEquals(List.of(Issue.UNPARSED), line.issues());
        assertTrue(line.hasBlockingIssue());
    }

    @Test
    void zeroQuantityIsBlocked() {
        ParsedLine line = one("doodh 0 packet, original price 30 discounted price 23");
        assertEquals(0, line.quantity());
        assertEquals(List.of(Issue.ZERO_QUANTITY), line.issues());
    }

    @Test
    void zeroOfferPriceIsMissingPrice() {
        ParsedLine line = one("doodh 6 packet, original price 30 discounted price 0");
        assertEquals(List.of(Issue.MISSING_PRICE), line.issues());
    }

    @Test
    void gibberishIsUnparsed() {
        ParsedLine line = one("hmm achha");
        assertEquals(List.of(Issue.UNPARSED), line.issues());
        assertEquals("hmm achha", line.rawPhrase());
        assertNull(line.itemKey());
    }

    // ---- Input variants ----

    @Test
    void caseAndRupeeWordsAreIgnored() {
        ParsedLine line = one("Doodh 6 Packet, Original Price 30 Rs Discounted Price 23 Rs");
        assertEquals("MILK", line.itemKey());
        assertMoney("23", line.offerPrice());
        assertMoney("30", line.originalPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void rupeeSymbolAndSlashDashAreIgnored() {
        ParsedLine line = one("doodh 6 packet, original price ₹30 discounted price ₹23/-");
        assertMoney("23", line.offerPrice());
        assertMoney("30", line.originalPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void numberWordsAreConverted() {
        ParsedLine line = one("doodh six packet, original price thirty discounted price twenty three");
        assertEquals(6, line.quantity());
        assertMoney("23", line.offerPrice());
        assertMoney("30", line.originalPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void newlinesSplitLines() {
        List<ParsedLine> lines = parser.parse(
                "doodh 6 packet, original price 30 discounted price 23\n"
                        + "bread 4 loaf, original price 35 discounted price 30");
        assertEquals(2, lines.size());
        assertEquals("MILK", lines.get(0).itemKey());
        assertEquals("BREAD", lines.get(1).itemKey());
    }

    @Test
    void windowsNewlinesSplitLines() {
        List<ParsedLine> lines = parser.parse(
                "doodh 6 packet, original price 30 discounted price 23\r\n"
                        + "bread 4 loaf, original price 35 discounted price 30");
        assertEquals(2, lines.size());
    }

    @Test
    void discountKeywordWithoutPriceWord() {
        ParsedLine line = one("doodh 6 packet, original price 30 discount 23");
        assertMoney("23", line.offerPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void unitSynonymsAreNormalised() {
        assertEquals("packet", one("doodh 6 packets, original price 30 discounted price 23").unit());
        assertEquals("kg", one("tamatar 5 kilo, original price 30 discounted price 15").unit());
    }

    @Test
    void devanagariItemNameIsRecognised() {
        ParsedLine line = one("दूध 6 packet, original price 30 discounted price 23");
        assertEquals("MILK", line.itemKey());
    }

    @Test
    void decimalPricesParseExactly() {
        ParsedLine line = one("doodh 6 packet, original price 22.50 discounted price 22");
        assertMoney("22.50", line.originalPrice());
        assertMoney("22", line.offerPrice());
        assertTrue(line.issues().isEmpty());
    }

    @Test
    void decimalQuantityIsUnparsed() {
        ParsedLine line = one("doodh 1.5 packet, original price 30 discounted price 23");
        assertEquals(List.of(Issue.UNPARSED), line.issues());
    }

    // ---- Joining and unknown items ----

    @Test
    void continuationStaysWithItsItem() {
        List<ParsedLine> lines = parser.parse("doodh 6 packet, original price 30 discounted price 23");
        assertEquals(1, lines.size());
    }

    @Test
    void unknownItemIsKeptWithWarning() {
        ParsedLine line = one("xyzzy 6 packet, original price 30 discounted price 23");
        assertNull(line.itemKey());
        assertEquals("xyzzy", line.itemName());
        assertEquals(List.of(Issue.UNKNOWN_ITEM), line.issues());
        assertFalse(line.hasBlockingIssue());
    }

    @Test
    void gibberishSegmentDoesNotStopOthers() {
        List<ParsedLine> lines = parser.parse(
                "hmm achha, doodh 6 packet, original price 30 discounted price 23");
        assertEquals(2, lines.size());
        assertEquals(List.of(Issue.UNPARSED), lines.get(0).issues());
        assertEquals("MILK", lines.get(1).itemKey());
        assertTrue(lines.get(1).issues().isEmpty());
    }

    // ---- Empty input ----

    @Test
    void emptyInputGivesNoLines() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }
}
