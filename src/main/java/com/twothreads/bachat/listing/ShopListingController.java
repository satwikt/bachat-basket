package com.twothreads.bachat.listing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.twothreads.bachat.listing.ListingDto.CreateRequest;
import com.twothreads.bachat.listing.ListingDto.CreateResponse;
import com.twothreads.bachat.listing.ListingDto.ListingsResponse;
import com.twothreads.bachat.listing.ListingDto.ParseRequest;
import com.twothreads.bachat.listing.ListingDto.ParseResponse;
import com.twothreads.bachat.listing.ListingDto.SoldOutResponse;

import jakarta.validation.Valid;

/** Thin HTTP layer for shop listings. The rules are in {@link ShopListingService}. */
@RestController
@RequestMapping("/api/shops/{shopId}/listings")
public class ShopListingController {

    private final ShopListingService service;

    public ShopListingController(ShopListingService service) {
        this.service = service;
    }

    @PostMapping("/parse")
    public ParseResponse parse(@PathVariable Long shopId, @Valid @RequestBody ParseRequest request) {
        return service.parse(shopId, request.transcript());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse create(@PathVariable Long shopId, @Valid @RequestBody CreateRequest request) {
        return service.create(shopId, request);
    }

    @GetMapping
    public ListingsResponse list(@PathVariable Long shopId) {
        return service.listActive(shopId);
    }

    @PostMapping("/sold-out")
    public SoldOutResponse soldOut(@PathVariable Long shopId) {
        return service.markAllSoldOut(shopId);
    }
}
