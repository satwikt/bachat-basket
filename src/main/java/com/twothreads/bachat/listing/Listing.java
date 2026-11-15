package com.twothreads.bachat.listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "listing")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "price_rupees", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceRupees;

    @Column(name = "original_price_rupees", precision = 10, scale = 2)
    private BigDecimal originalPriceRupees;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_price_source", length = 20)
    private OriginalPriceSource originalPriceSource;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "sell_today", nullable = false)
    private boolean sellToday;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status = ListingStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ListingSource source = ListingSource.MANUAL;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "raw_phrase", length = 300)
    private String rawPhrase;

    @Column(name = "item_key", length = 60)
    private String itemKey;

    protected Listing() {
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPriceRupees() { return priceRupees; }
    public void setPriceRupees(BigDecimal priceRupees) { this.priceRupees = priceRupees; }
    public BigDecimal getOriginalPriceRupees() { return originalPriceRupees; }
    public void setOriginalPriceRupees(BigDecimal originalPriceRupees) { this.originalPriceRupees = originalPriceRupees; }
    public OriginalPriceSource getOriginalPriceSource() { return originalPriceSource; }
    public void setOriginalPriceSource(OriginalPriceSource originalPriceSource) { this.originalPriceSource = originalPriceSource; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public boolean isSellToday() { return sellToday; }
    public void setSellToday(boolean sellToday) { this.sellToday = sellToday; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(OffsetDateTime endsAt) { this.endsAt = endsAt; }
    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }
    public ListingSource getSource() { return source; }
    public void setSource(ListingSource source) { this.source = source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getRawPhrase() { return rawPhrase; }
    public void setRawPhrase(String rawPhrase) { this.rawPhrase = rawPhrase; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
}
