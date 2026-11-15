package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Item words as they come back from speech, in each script. */
class VoiceListingItemWordsTest {

    private final SynonymDictionary dictionary = new SynonymDictionary();

    @Test
    void hindiScriptMilkWordIsMilk() {
        assertEquals("MILK", dictionary.lookup("मिल्क").orElseThrow().key());
    }

    @Test
    void hindiScriptMilkInAListing() {
        ParsedLine line = new VoiceListingParser().parse("मिल्क 6 पैकेट, ओरिजिनल प्राइस 30 डिस्काउंटेड प्राइस 23").get(0);
        assertEquals("MILK", line.itemKey());
    }
}
