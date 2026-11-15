package com.twothreads.bachat.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twothreads.bachat.customer.entity.Listing;
import com.twothreads.bachat.customer.repositories.ListingRepository;
import com.twothreads.bachat.listing.ListingDto.CreateRequest;
import com.twothreads.bachat.listing.ListingDto.CreateResponse;
import com.twothreads.bachat.listing.ListingDto.LineRequest;
import com.twothreads.bachat.listing.ListingDto.ListingView;
import com.twothreads.bachat.listing.ListingDto.ListingsResponse;
import com.twothreads.bachat.listing.ListingDto.ParseResponse;
import com.twothreads.bachat.listing.ListingDto.ParsedLineView;
import com.twothreads.bachat.listing.ListingDto.ShopView;
import com.twothreads.bachat.listing.ListingDto.SoldOutResponse;
import com.twothreads.bachat.shop.Shop;
import com.twothreads.bachat.shop.ShopRepository;

/** Parse, save, list and sold-out for one shop's listings. Business rules live here, not in the controller. */
@Service
public class ShopListingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime DEFAULT_CLOSING = LocalTime.of(23, 59);
    private static final DateTimeFormatter CLOSING_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_UNIT_LENGTH = 30;
    private static final int MAX_RAW_PHRASE_LENGTH = 300;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final ShopRepository shops;
    private final ListingRepository listings;
    private final Clock clock;
    private final SynonymDictionary dictionary = new SynonymDictionary();
    private final VoiceListingParser parser = new VoiceListingParser(dictionary);

    public ShopListingService(ShopRepository shops, ListingRepository listings, Clock clock) {
        this.shops = shops;
        this.listings = listings;
        this.clock = clock;
    }

    /** Preview only: parses the transcript and writes nothing. */
    @Transactional(readOnly = true)
    public ParseResponse parse(Long shopId, String transcript) {
        findShop(shopId);
        List<ParsedLineView> lines = new ArrayList<>();
        boolean blocking = false;
        for (ParsedLine line : parser.parse(transcript)) {
            blocking |= line.hasBlockingIssue();
            lines.add(new ParsedLineView(
                    line.rawPhrase(),
                    line.itemKey(),
                    line.itemName(),
                    line.quantity(),
                    line.unit(),
                    line.offerPrice(),
                    line.originalPrice(),
                    line.issues().stream().map(Enum::name).toList()));
        }
        return new ParseResponse(lines, blocking);
    }

    /**
     * Saves the confirmed lines as ACTIVE SPEECH listings. Every line is checked and every end time is worked
     * out before anything is written, so one bad line saves nothing.
     */
    @Transactional
    public CreateResponse create(Long shopId, CreateRequest request) {
        Shop shop = findShop(shopId);
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);

        List<Listing> toSave = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            int lineNo = i + 1;
            LineRequest line = request.lines().get(i);

            List<Issue> issues = checkLine(line);
            Issue blocking = issues.stream().filter(Issue::blocking).findFirst().orElse(null);
            if (blocking != null) {
                throw new ListingValidationException(
                        "line " + lineNo + " has a blocking issue: " + blocking.name());
            }
            toSave.add(toEntity(shopId, line, endsAt(shop, line, now, lineNo)));
        }

        List<Listing> saved = listings.saveAll(toSave);
        return new CreateResponse(saved.size(), saved.stream().map(Listing::getId).toList());
    }

    /** Shopper view: active listings that have not ended yet, soonest end first. */
    @Transactional(readOnly = true)
    public ListingsResponse listActive(Long shopId) {
        Shop shop = findShop(shopId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<ListingView> views = listings
                .findByShopIdAndStatusAndEndsAtAfterOrderByEndsAtAsc(shopId, Listing.Status.ACTIVE, now)
                .stream()
                .map(this::toView)
                .toList();
        String closing = shop.getClosingTime() == null ? null : shop.getClosingTime().format(CLOSING_FORMAT);
        return new ListingsResponse(new ShopView(shop.getId(), shop.getName(), closing), views);
    }

    @Transactional
    public SoldOutResponse markAllSoldOut(Long shopId) {
        findShop(shopId);
        List<Listing> active = listings.findByShopIdAndStatus(shopId, Listing.Status.ACTIVE);
        active.forEach(l -> l.setStatus(Listing.Status.SOLD_OUT));
        return new SoldOutResponse(active.size());
    }

    private Shop findShop(Long shopId) {
        return shops.findById(shopId).orElseThrow(() -> new ShopNotFoundException(shopId));
    }

    /** Server-side re-check of the blocking rules (spec 7.2). The client's flags are not trusted. */
    private List<Issue> checkLine(LineRequest line) {
        List<Issue> issues = new ArrayList<>();
        String name = line.itemName() == null ? "" : line.itemName().trim();
        String unit = line.unit() == null ? "" : line.unit().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || unit.length() > MAX_UNIT_LENGTH) {
            issues.add(Issue.UNPARSED);
        }
        if (line.quantity() == null || line.quantity() <= 0) {
            issues.add(Issue.ZERO_QUANTITY);
        }
        BigDecimal offer = line.offerPrice();
        BigDecimal original = line.originalPrice();
        if (offer == null || offer.signum() <= 0) {
            issues.add(Issue.MISSING_PRICE);
        }
        if (original == null || original.signum() <= 0) {
            issues.add(Issue.ORIGINAL_PRICE_MISSING);
        } else if (offer != null && offer.signum() > 0 && offer.compareTo(original) >= 0) {
            issues.add(Issue.PRICE_ABOVE_ORIGINAL);
        }
        if ((offer != null && offer.compareTo(MAX_PRICE) > 0)
                || (original != null && original.compareTo(MAX_PRICE) > 0)) {
            issues.add(Issue.UNPARSED);
        }
        return issues;
    }

    /**
     * End time in Asia/Kolkata (spec 7.2):
     * closing time today (tomorrow if that has passed); if an expiry date is given and the item is not
     * "sell today", the earlier of that and 23:59:59 on the expiry date. If it is not in the future, reject.
     */
    private OffsetDateTime endsAt(Shop shop, LineRequest line, ZonedDateTime now, int lineNo) {
        LocalTime closing = shop.getClosingTime() == null ? DEFAULT_CLOSING : shop.getClosingTime();
        LocalDate today = now.toLocalDate();

        ZonedDateTime closeAt = today.atTime(closing).atZone(ZONE);
        if (!closeAt.isAfter(now)) {
            closeAt = today.plusDays(1).atTime(closing).atZone(ZONE);
        }

        ZonedDateTime ends = closeAt;
        boolean sellToday = Boolean.TRUE.equals(line.sellToday());
        if (line.expiryDate() != null && !sellToday) {
            ZonedDateTime expiryEnd = line.expiryDate().atTime(23, 59, 59).atZone(ZONE);
            if (expiryEnd.isBefore(ends)) {
                ends = expiryEnd;
            }
            if (!ends.isAfter(now)) {
                throw new ListingValidationException("line " + lineNo + ": item already expired");
            }
        }
        return ends.toOffsetDateTime();
    }

    private Listing toEntity(Long shopId, LineRequest line, OffsetDateTime endsAt) {
        Listing listing = new Listing();
        listing.setShopId(shopId);
        listing.setItemName(line.itemName().trim());
        listing.setItemKey(dictionary.lookup(line.itemName()).map(SynonymDictionary.Item::key).orElse(null));
        listing.setQuantity(line.quantity());
        listing.setUnit(line.unit() == null || line.unit().isBlank() ? "pc" : line.unit().trim());
        listing.setPriceRupees(line.offerPrice().setScale(2, RoundingMode.HALF_UP));
        listing.setOriginalPriceRupees(line.originalPrice().setScale(2, RoundingMode.HALF_UP));
        listing.setRawPhrase(truncate(line.rawPhrase(), MAX_RAW_PHRASE_LENGTH));
        listing.setExpiryDate(line.expiryDate());
        listing.setSellToday(Boolean.TRUE.equals(line.sellToday()));
        listing.setEndsAt(endsAt);
        listing.setStatus(Listing.Status.ACTIVE);
        listing.setSource(Listing.Source.SPEECH);
        return listing;
    }

    private ListingView toView(Listing l) {
        BigDecimal offer = l.getPriceRupees();
        BigDecimal original = l.getOriginalPriceRupees();
        Integer discount = null;
        if (original != null && original.signum() > 0) {
            discount = original.subtract(offer).multiply(BigDecimal.valueOf(100))
                    .divide(original, 0, RoundingMode.HALF_UP).intValue();
        }
        return new ListingView(
                l.getId(),
                l.getItemName(),
                l.getQuantity(),
                l.getUnit(),
                offer,
                original,
                discount,
                l.getEndsAt().atZoneSameInstant(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
