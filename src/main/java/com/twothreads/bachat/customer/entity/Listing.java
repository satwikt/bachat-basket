package com.twothreads.bachat.customer.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "listing", schema = "public")
public class Listing {

    public enum Status { ACTIVE, SOLD_OUT, EXPIRED }

    public enum Source { MANUAL, SPEECH, PHOTO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 30)
    private String unit;

    @Column(name = "price_rupees", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceRupees;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "sell_today", nullable = false)
    private boolean sellToday = false;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.MANUAL;

    // filled by DB default CURRENT_TIMESTAMP
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

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
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public boolean isSellToday() { return sellToday; }
    public void setSellToday(boolean sellToday) { this.sellToday = sellToday; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(OffsetDateTime endsAt) { this.endsAt = endsAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
