package com.twothreads.bachat.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.twothreads.bachat.customer.entity.Listing;
import com.twothreads.bachat.customer.entity.Subscription;

class DtoMappingTest {

    private static Listing listing(String offer, String original) {
        Listing l = new Listing();
        l.setShopId(1L);
        l.setItemName("Milk");
        l.setItemKey("MILK");
        l.setQuantity(6);
        l.setUnit("packet");
        l.setPriceRupees(new BigDecimal(offer));
        l.setOriginalPriceRupees(original == null ? null : new BigDecimal(original));
        l.setEndsAt(OffsetDateTime.parse("2026-11-15T21:00:00+05:30"));
        l.setRawPhrase("doodh 6 packet original price 30 discounted price 23");
        return l;
    }

    @Test
    void listingDtoCopiesFieldsAndNamesEnums() {
        ListingDto dto = ListingDto.from(listing("23", "30"));

        assertEquals("MILK", dto.itemKey());
        assertEquals("Milk", dto.itemName());
        assertEquals(6, dto.quantity());
        assertEquals("packet", dto.unit());
        assertEquals(0, new BigDecimal("23").compareTo(dto.priceRupees()));
        assertEquals(0, new BigDecimal("30").compareTo(dto.originalPriceRupees()));
        assertEquals("ACTIVE", dto.status());
        assertEquals("MANUAL", dto.source());
        assertFalse(dto.sellToday());
    }

    @Test
    void discountIsRoundedHalfUp() {
        assertEquals(23, ListingDto.from(listing("23", "30")).discountPercent()); // 23.33
        assertEquals(14, ListingDto.from(listing("30", "35")).discountPercent()); // 14.28
        assertEquals(50, ListingDto.from(listing("15", "30")).discountPercent());
    }

    @Test
    void discountIsNullWithoutAPositiveOriginal() {
        assertNull(ListingDto.from(listing("23", null)).discountPercent());
        assertNull(ListingDto.from(listing("23", "0")).discountPercent());
    }

    @Test
    void listingDtoHasNoRawPhraseField() {
        // the spoken phrase is audit-only and is not part of the read model
        boolean hasRawPhrase = java.util.Arrays.stream(ListingDto.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("rawPhrase"));
        assertFalse(hasRawPhrase);
    }

    @Test
    void subscriptionDtoCopiesFields() {
        Subscription s = new Subscription(7L, 2L);
        s.setActive(false);

        SubscriptionDto dto = SubscriptionDto.from(s);

        assertEquals(7L, dto.customerId());
        assertEquals(2L, dto.shopId());
        assertFalse(dto.active());
        assertTrue(dto.id() == null); // not saved yet
    }
}
