package com.twothreads.bachat.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.twothreads.bachat.customer.entity.Listing;
import com.twothreads.bachat.customer.repositories.ListingRepository;
import com.twothreads.bachat.listing.ListingApi.CreateRequest;
import com.twothreads.bachat.listing.ListingApi.LineRequest;
import com.twothreads.bachat.listing.ListingApi.ListingView;

// Use the migrated schema (Flyway V1..V3) with validation, like the app. The default
// @DataJpaTest database is built by Hibernate from the entities, which does not match the migrations.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(ShopListingService.class)
class ShopListingServiceTest {

    private static final long SHOP_ID = 1L; // seeded by V2: Sharma General Store, closes 21:00 IST
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @TestConfiguration
    static class Config {
        @Bean
        MutableClock clock() {
            // 2026-11-15 10:00 IST
            return new MutableClock(Instant.parse("2026-11-15T04:30:00Z"), ZONE);
        }
    }

    /** A clock the test can move. */
    static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void at(String isoWithOffset) {
            this.instant = OffsetDateTime.parse(isoWithOffset).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return new MutableClock(instant, z);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired ShopListingService service;
    @Autowired ListingRepository listings;
    @Autowired MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.at("2026-11-15T10:00:00+05:30");
    }

    private static LineRequest milk(LocalDate expiry, Boolean sellToday) {
        return new LineRequest("doodh 6 packet original price 30 discounted price 23",
                null, "Milk", 6, "packet", new BigDecimal("23"), new BigDecimal("30"), sellToday, expiry);
    }

    private OffsetDateTime endsAfterSaving(LineRequest line) {
        Long id = service.create(SHOP_ID, new CreateRequest(List.of(line))).listingIds().get(0);
        return listings.findById(id).orElseThrow().getEndsAt();
    }

    private static void assertEndsAt(String expected, OffsetDateTime actual) {
        assertEquals(OffsetDateTime.parse(expected).toInstant(), actual.toInstant());
    }

    // ---- ends_at rules (spec 7.2) ----

    @Test
    void endsAtIsClosingTimeTodayWhenNoExpiry() {
        assertEndsAt("2026-11-15T21:00:00+05:30", endsAfterSaving(milk(null, false)));
    }

    @Test
    void endsAtIsClosingTimeWhenExpiryIsLater() {
        assertEndsAt("2026-11-15T21:00:00+05:30", endsAfterSaving(milk(LocalDate.of(2026, 11, 16), false)));
    }

    @Test
    void endsAtIsExpiryEndWhenExpiryIsToday() {
        // closing 21:00 is earlier than 23:59:59 on the expiry date, so closing wins
        assertEndsAt("2026-11-15T21:00:00+05:30", endsAfterSaving(milk(LocalDate.of(2026, 11, 15), false)));
    }

    @Test
    void sellTodayIgnoresExpiryDate() {
        assertEndsAt("2026-11-15T21:00:00+05:30", endsAfterSaving(milk(LocalDate.of(2026, 11, 15), true)));
    }

    @Test
    void afterClosingUsesTomorrowsClosingTime() {
        clock.at("2026-11-15T22:00:00+05:30");
        assertEndsAt("2026-11-16T21:00:00+05:30", endsAfterSaving(milk(null, false)));
    }

    @Test
    void afterClosingWithExpiryTodayEndsAtEndOfExpiryDay() {
        clock.at("2026-11-15T22:00:00+05:30");
        assertEndsAt("2026-11-15T23:59:59+05:30", endsAfterSaving(milk(LocalDate.of(2026, 11, 15), false)));
    }

    @Test
    void expiredItemIsRejected() {
        ListingValidationException e = assertThrows(ListingValidationException.class,
                () -> service.create(SHOP_ID, new CreateRequest(List.of(milk(LocalDate.of(2026, 11, 14), false)))));
        assertEquals("line 1: item already expired", e.getMessage());
    }

    // ---- Save rules ----

    @Test
    void blockingLineSavesNothing() {
        LineRequest good = milk(null, false);
        LineRequest bad = new LineRequest("doodh 6 packet original price 30 discounted price 30",
                null, "Milk", 6, "packet", new BigDecimal("30"), new BigDecimal("30"), false, null);

        ListingValidationException e = assertThrows(ListingValidationException.class,
                () -> service.create(SHOP_ID, new CreateRequest(List.of(good, bad))));

        assertEquals("line 2 has a blocking issue: PRICE_ABOVE_ORIGINAL", e.getMessage());
        assertEquals(0, listings.findByShopIdAndStatus(SHOP_ID, Listing.Status.ACTIVE).size());
    }

    @Test
    void unknownShopIsNotFound() {
        assertThrows(ShopNotFoundException.class,
                () -> service.create(99L, new CreateRequest(List.of(milk(null, false)))));
    }

    @Test
    void itemKeyIsDerivedFromName() {
        Long id = service.create(SHOP_ID, new CreateRequest(List.of(milk(null, false)))).listingIds().get(0);
        assertEquals("MILK", listings.findById(id).orElseThrow().getItemKey());
    }

    // ---- Shopper list ----

    @Test
    void listExcludesSoldOutExpiredAndPastListings() {
        service.create(SHOP_ID, new CreateRequest(List.of(milk(null, false))));
        Listing soldOut = listing(Listing.Status.SOLD_OUT, "2026-11-15T21:00:00+05:30");
        Listing expired = listing(Listing.Status.EXPIRED, "2026-11-15T21:00:00+05:30");
        Listing past = listing(Listing.Status.ACTIVE, "2026-11-15T09:00:00+05:30");
        listings.saveAll(List.of(soldOut, expired, past));

        ListingApi.ListingsResponse response = service.listActive(SHOP_ID);

        assertEquals(1, response.listings().size());
        assertEquals("Milk", response.listings().get(0).itemName());
        assertEquals("21:00", response.shop().closingTime());
    }

    @Test
    void discountPercentIsComputedOnServer() {
        service.create(SHOP_ID, new CreateRequest(List.of(milk(null, false)))); // 23 from 30
        LineRequest bread = new LineRequest("bread 4 loaf original price 35 discounted price 30",
                null, "Bread", 4, "loaf", new BigDecimal("30"), new BigDecimal("35"), false, null);
        service.create(SHOP_ID, new CreateRequest(List.of(bread))); // 14.28 -> 14

        List<ListingView> views = service.listActive(SHOP_ID).listings();

        assertTrue(views.stream().anyMatch(v -> v.discountPercent() == 23));
        assertTrue(views.stream().anyMatch(v -> v.discountPercent() == 14));
    }

    @Test
    void soldOutMarksActiveListings() {
        service.create(SHOP_ID, new CreateRequest(List.of(milk(null, false), milk(null, false))));

        ListingApi.SoldOutResponse response = service.markAllSoldOut(SHOP_ID);

        assertEquals(2, response.updated());
        assertEquals(0, service.listActive(SHOP_ID).listings().size());
    }

    private Listing listing(Listing.Status status, String endsAt) {
        Listing l = new Listing();
        l.setShopId(SHOP_ID);
        l.setItemName("Old milk");
        l.setQuantity(1);
        l.setUnit("packet");
        l.setPriceRupees(new BigDecimal("10"));
        l.setOriginalPriceRupees(new BigDecimal("20"));
        l.setEndsAt(OffsetDateTime.parse(endsAt));
        l.setStatus(status);
        l.setSource(Listing.Source.SPEECH);
        return l;
    }
}
