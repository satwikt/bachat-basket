package com.twothreads.bachat.customer.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twothreads.bachat.customer.CustomerNotFoundException;
import com.twothreads.bachat.customer.entity.Customer;
import com.twothreads.bachat.customer.entity.Subscription;
import com.twothreads.bachat.customer.repositories.CustomerRepository;
import com.twothreads.bachat.customer.repositories.SubscribersRepositories;
import com.twothreads.bachat.dto.FollowedShopDto;
import com.twothreads.bachat.listing.ShopNotFoundException;
import com.twothreads.bachat.shop.Shop;
import com.twothreads.bachat.shop.ShopRepository;

/** Anonymous customers and the shops they follow (spec customer-ask 5.6). */
@Service
public class CustomerAccountService {

    private final CustomerRepository customers;
    private final ShopRepository shops;
    private final SubscribersRepositories subscriptions;

    public CustomerAccountService(CustomerRepository customers, ShopRepository shops,
                                  SubscribersRepositories subscriptions) {
        this.customers = customers;
        this.shops = shops;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public Long create(String name) {
        String trimmed = name == null || name.isBlank() ? null : name.trim();
        return customers.save(new Customer(trimmed)).getId();
    }

    @Transactional(readOnly = true)
    public void requireCustomer(Long customerId) {
        if (!customers.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }
    }

    /** Shops the customer currently follows. */
    @Transactional(readOnly = true)
    public List<FollowedShopDto> followedShops(Long customerId) {
        requireCustomer(customerId);
        List<Long> shopIds = subscriptions.findByActiveTrueAndCustomerId(customerId)
                .stream().map(Subscription::getShopId).toList();
        if (shopIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = shops.findAllById(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::getName));
        return shopIds.stream()
                .map(id -> new FollowedShopDto(id, names.get(id)))
                .toList();
    }

    /**
     * Idempotent: a new follow inserts the row, an existing one (active or not) is set active.
     * If a concurrent insert wins the unique key, the row is re-read and set active.
     */
    @Transactional
    public void follow(Long customerId, Long shopId) {
        requireCustomerAndShop(customerId, shopId);
        Subscription existing = subscriptions.findByCustomerIdAndShopId(customerId, shopId).orElse(null);
        if (existing != null) {
            existing.setActive(true);
            return;
        }
        try {
            subscriptions.saveAndFlush(new Subscription(customerId, shopId));
        } catch (DataIntegrityViolationException e) {
            subscriptions.findByCustomerIdAndShopId(customerId, shopId).ifPresent(s -> s.setActive(true));
        }
    }

    /** Idempotent: deactivates the row if there is one. */
    @Transactional
    public void unfollow(Long customerId, Long shopId) {
        requireCustomerAndShop(customerId, shopId);
        subscriptions.findByCustomerIdAndShopId(customerId, shopId).ifPresent(s -> s.setActive(false));
    }

    private void requireCustomerAndShop(Long customerId, Long shopId) {
        requireCustomer(customerId);
        if (!shops.existsById(shopId)) {
            throw new ShopNotFoundException(shopId);
        }
    }
}
