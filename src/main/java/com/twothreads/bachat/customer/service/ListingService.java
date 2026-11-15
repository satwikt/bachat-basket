package com.twothreads.bachat.customer.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twothreads.bachat.customer.entity.Listing;
import com.twothreads.bachat.customer.repositories.ListingRepository;

@Service
public class ListingService {

    private final ListingRepository listingRepository;

    public ListingService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    @Transactional
    public Listing create(Listing listing) {
        if (listing.getEndsAt() == null || !listing.getEndsAt().isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("endsAt must be in the future");
        }
        listing.setStatus(Listing.Status.ACTIVE);
        return listingRepository.save(listing);
    }

    @Transactional(readOnly = true)
    public Listing get(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Listing " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Listing> activeForShop(Long shopId) {
        return listingRepository.findByShopIdAndStatusAndEndsAtAfterOrderByEndsAtAsc(
                shopId, Listing.Status.ACTIVE, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Listing> allForShop(Long shopId) {
        return listingRepository.findByShopIdOrderByCreatedAtDesc(shopId);
    }

    @Transactional
    public Listing markSoldOut(Long id) {
        Listing listing = get(id);
        listing.setStatus(Listing.Status.SOLD_OUT);
        return listing;
    }

    @Transactional
    public void delete(Long id) {
        listingRepository.deleteById(id);
    }

    /** Call from a @Scheduled job (e.g. every minute) to retire overdue listings. */
    @Transactional
    public int expireOverdue() {
        return listingRepository.expireOverdue(OffsetDateTime.now());
    }
}
