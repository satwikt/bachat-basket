package com.twothreads.bachat.customer.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twothreads.bachat.customer.entity.Listing;
import com.twothreads.bachat.customer.entity.Subscription;
import com.twothreads.bachat.customer.repositories.ListingRepository;
import com.twothreads.bachat.customer.repositories.SubscribersRepositories;

@Service
public class CustomerService {

    private final SubscribersRepositories subscribersRepositories;
    private final ListingRepository listingRepository;

    public CustomerService(SubscribersRepositories subscribersRepositories, ListingRepository listingRepository ) {
        this.subscribersRepositories = subscribersRepositories;
        this.listingRepository = listingRepository;
    }
  

    /**
     * Returns shopId -> active listings for every shop the customer is subscribed to.
     * Subscribed shops with no active listings are included with an empty list.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Listing>> getListingsByShop(Long customerId) {
        List<Long> shopIds = subscribersRepositories.findByActiveTrueAndCustomerId(customerId)
                .stream()
                .map(Subscription::getShopId)
                .distinct()
                .toList();

        if (shopIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<Listing>> result = new LinkedHashMap<>();
        shopIds.forEach(id -> result.put(id, new ArrayList<>()));

        listingRepository
                .findByShopIdInAndStatusAndEndsAtAfterOrderByEndsAtAsc(
                        shopIds, Listing.Status.ACTIVE, OffsetDateTime.now())
                .forEach(l -> result.get(l.getShopId()).add(l));

        return result;
    }
}
