package com.twothreads.bachat.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.twothreads.bachat.customer.entity.Listing;

/**
 * Read model of a {@link Listing}. The raw spoken phrase is left out: it is kept for audit only.
 * The discount is computed here so every service reports it the same way.
 */
public record ListingDto(
        Long id,
        Long shopId,
        String itemKey,
        String itemName,
        Integer quantity,
        String unit,
        BigDecimal priceRupees,
        BigDecimal originalPriceRupees,
        Integer discountPercent,
        LocalDate expiryDate,
        boolean sellToday,
        OffsetDateTime endsAt,
        String status,
        String source,
        OffsetDateTime createdAt) {

    public static ListingDto from(Listing l) {
        return new ListingDto(
                l.getId(),
                l.getShopId(),
                l.getItemKey(),
                l.getItemName(),
                l.getQuantity(),
                l.getUnit(),
                l.getPriceRupees(),
                l.getOriginalPriceRupees(),
                discountPercent(l.getOriginalPriceRupees(), l.getPriceRupees()),
                l.getExpiryDate(),
                l.isSellToday(),
                l.getEndsAt(),
                l.getStatus() == null ? null : l.getStatus().name(),
                l.getSource() == null ? null : l.getSource().name(),
                l.getCreatedAt());
    }

    /** (original - offer) / original * 100, rounded HALF_UP. Null when the original price is missing or zero. */
    public static Integer discountPercent(BigDecimal original, BigDecimal offer) {
        if (original == null || offer == null || original.signum() <= 0) {
            return null;
        }
        return original.subtract(offer).multiply(BigDecimal.valueOf(100))
                .divide(original, 0, RoundingMode.HALF_UP).intValue();
    }
}
