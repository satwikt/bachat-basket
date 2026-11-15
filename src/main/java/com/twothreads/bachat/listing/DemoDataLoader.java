package com.twothreads.bachat.listing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.twothreads.bachat.customer.entity.Listing;
import com.twothreads.bachat.customer.repositories.ListingRepository;

/**
 * Demo profile only. On an empty demo database, lists one Milk offer at Matoshree Fresh Mart (shop 2),
 * ending at its closing time (23:00). Not run in live mode or in tests.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements CommandLineRunner {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final long MATOSHREE_FRESH_MART = 2L;
    private static final LocalTime CLOSING = LocalTime.of(23, 0);

    private final ListingRepository listings;
    private final Clock clock;

    public DemoDataLoader(ListingRepository listings, Clock clock) {
        this.listings = listings;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (listings.count() > 0) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        ZonedDateTime endsAt = now.toLocalDate().atTime(CLOSING).atZone(ZONE);
        if (!endsAt.isAfter(now)) {
            endsAt = endsAt.plusDays(1);
        }

        Listing milk = new Listing();
        milk.setShopId(MATOSHREE_FRESH_MART);
        milk.setItemName("Milk");
        milk.setItemKey("MILK");
        milk.setQuantity(6);
        milk.setUnit("packet");
        milk.setPriceRupees(new BigDecimal("23.00"));
        milk.setOriginalPriceRupees(new BigDecimal("30.00"));
        milk.setRawPhrase("demo: milk 6 packet original price 30 discounted price 23");
        milk.setSellToday(false);
        milk.setEndsAt(endsAt.toOffsetDateTime());
        milk.setStatus(Listing.Status.ACTIVE);
        milk.setSource(Listing.Source.SPEECH);
        listings.save(milk);
    }
}
