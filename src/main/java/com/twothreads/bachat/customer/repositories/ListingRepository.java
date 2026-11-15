package com.twothreads.bachat.customer.repositories;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.twothreads.bachat.customer.entity.Listing;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {

    // matches idx_listing_shop_status (shop_id, status, ends_at)
    List<Listing> findByShopIdAndStatusAndEndsAtAfterOrderByEndsAtAsc(
            Long shopId, Listing.Status status, OffsetDateTime now);

    List<Listing> findByShopIdOrderByCreatedAtDesc(Long shopId);

    // used by "mark all sold out" (any end time)
    List<Listing> findByShopIdAndStatus(Long shopId, Listing.Status status);

    List<Listing> findByShopIdInAndStatusAndEndsAtAfterOrderByEndsAtAsc(
            List<Long> shopIds, Listing.Status status, OffsetDateTime now);

    // listings from every shop a customer is subscribed to
    @Query("""
           select l from Listing l
           where l.status = com.twothreads.bachat.customer.entity.Listing.Status.ACTIVE
             and l.endsAt > :now
             and l.shopId in (select s.shopId from Subscription s
                              where s.customerId = :customerId and s.active = true)
           order by l.endsAt asc
           """)
    List<Listing> findActiveForSubscriber(@Param("customerId") Long customerId,
                                          @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update Listing l set l.status = com.twothreads.bachat.customer.entity.Listing.Status.EXPIRED "
         + "where l.status = com.twothreads.bachat.customer.entity.Listing.Status.ACTIVE and l.endsAt <= :now")
    int expireOverdue(@Param("now") OffsetDateTime now);
}