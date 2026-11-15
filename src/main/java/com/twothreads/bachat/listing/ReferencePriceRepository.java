package com.twothreads.bachat.listing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferencePriceRepository extends JpaRepository<ReferencePrice, Long> {

    List<ReferencePrice> findByItemKey(String itemKey);
}
