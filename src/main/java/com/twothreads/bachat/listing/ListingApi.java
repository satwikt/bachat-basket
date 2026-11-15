package com.twothreads.bachat.listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the listing endpoints (spec section 7). */
public final class ListingApi {

    private ListingApi() {
    }

    // ---- POST /listings/parse ----

    public record ParseRequest(
            @NotBlank(message = "transcript is required")
            @Size(max = 2000, message = "transcript too long")
            String transcript) {
    }

    public record ParseResponse(List<ParsedLineView> lines, boolean blocking) {
    }

    public record ParsedLineView(
            String rawPhrase,
            String itemKey,
            String itemName,
            Integer quantity,
            String unit,
            BigDecimal offerPrice,
            BigDecimal originalPrice,
            List<String> issues) {
    }

    // ---- POST /listings ----

    public record CreateRequest(
            @NotEmpty(message = "lines is required")
            @Size(max = 50, message = "too many lines")
            List<@Valid LineRequest> lines) {
    }

    public record LineRequest(
            String rawPhrase,
            String itemKey,
            String itemName,
            Integer quantity,
            String unit,
            BigDecimal offerPrice,
            BigDecimal originalPrice,
            Boolean sellToday,
            LocalDate expiryDate) {
    }

    public record CreateResponse(int created, List<Long> listingIds) {
    }

    // ---- GET /listings ----

    public record ListingsResponse(ShopView shop, List<ListingView> listings) {
    }

    public record ShopView(Long id, String name, String closingTime) {
    }

    public record ListingView(
            Long id,
            String itemName,
            Integer quantity,
            String unit,
            BigDecimal offerPrice,
            BigDecimal originalPrice,
            Integer discountPercent,
            String endsAt) {
    }

    // ---- POST /listings/sold-out ----

    public record SoldOutResponse(int updated) {
    }
}
