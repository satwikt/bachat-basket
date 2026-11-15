package com.twothreads.bachat.listing;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reference_price")
public class ReferencePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_key", nullable = false, length = 60)
    private String itemKey;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "usual_price_rupees", nullable = false, precision = 10, scale = 2)
    private BigDecimal usualPriceRupees;

    protected ReferencePrice() {
    }

    public Long getId() { return id; }
    public String getItemKey() { return itemKey; }
    public String getUnit() { return unit; }
    public BigDecimal getUsualPriceRupees() { return usualPriceRupees; }
}
