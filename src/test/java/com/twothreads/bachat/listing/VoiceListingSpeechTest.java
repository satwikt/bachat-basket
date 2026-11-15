package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Wordings that speech recognition commonly produces for the same two-price template. */
class VoiceListingSpeechTest {

    private final VoiceListingParser parser = new VoiceListingParser();

    private static void assertMilk30to23(String transcript) {
        List<ParsedLine> lines = new VoiceListingParser().parse(transcript);
        assertEquals(1, lines.size(), transcript);
        ParsedLine line = lines.get(0);
        assertEquals("MILK", line.itemKey(), transcript);
        assertEquals(6, line.quantity(), transcript);
        assertEquals(0, new BigDecimal("23").compareTo(line.offerPrice()), transcript);
        assertEquals(0, new BigDecimal("30").compareTo(line.originalPrice()), transcript);
        assertTrue(line.issues().isEmpty(), transcript + " -> " + line.issues());
    }

    @Test
    void trailingFullStopIsIgnored() {
        assertMilk30to23("doodh 6 packet, original price 30, discounted price 23.");
    }

    @Test
    void fillerIsIsIgnored() {
        assertMilk30to23("doodh 6 packet, original price is 30 discounted price is 23");
    }

    @Test
    void hyphenatedNumberWordsAreSummed() {
        assertMilk30to23("doodh six packet, original price thirty, discounted price twenty-three");
    }

    @Test
    void commaSeparatedPricesWithoutPriceWord() {
        assertMilk30to23("doodh 6 packet, original 30, discounted 23");
    }

    @Test
    void discountPriceWordingWorks() {
        assertMilk30to23("doodh 6 packet original price 30 discount price 23");
    }

    @Test
    void rupeesWordsAroundPricesAreIgnored() {
        assertMilk30to23("doodh 6 packet, original price 30 rupees discounted price 23 rupees.");
    }

    @Test
    void discountedWithoutOriginalStaysUnparsed() {
        List<ParsedLine> lines = parser.parse("doodh 6 packet, discounted price 23.");
        assertEquals(List.of(Issue.UNPARSED), lines.get(0).issues());
    }
}
