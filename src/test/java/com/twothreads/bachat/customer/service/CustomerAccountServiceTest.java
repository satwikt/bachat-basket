package com.twothreads.bachat.customer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.twothreads.bachat.customer.CustomerNotFoundException;
import com.twothreads.bachat.customer.entity.Subscription;
import com.twothreads.bachat.customer.repositories.SubscribersRepositories;
import com.twothreads.bachat.dto.FollowedShopDto;
import com.twothreads.bachat.listing.ShopNotFoundException;

// Migrated schema with validation, like the app. Shop 1 is seeded by V2.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(CustomerAccountService.class)
class CustomerAccountServiceTest {

    private static final long SHOP = 1L;

    @Autowired CustomerAccountService accounts;
    @Autowired SubscribersRepositories subscriptions;

    @Test
    void newCustomerFollowsNothing() {
        Long id = accounts.create("Asha");
        assertTrue(accounts.followedShops(id).isEmpty());
    }

    @Test
    void followAddsTheShop() {
        Long id = accounts.create("Asha");
        accounts.follow(id, SHOP);

        assertEquals(List.of(new FollowedShopDto(SHOP, "Sharma General Store")), accounts.followedShops(id));
    }

    @Test
    void followingTwiceKeepsOneRow() {
        Long id = accounts.create("Asha");
        accounts.follow(id, SHOP);
        accounts.follow(id, SHOP);

        assertEquals(1, subscriptions.findByCustomerId(id).size());
    }

    @Test
    void unfollowThenFollowReactivatesTheSameRow() {
        Long id = accounts.create("Asha");
        accounts.follow(id, SHOP);
        Long rowBefore = subscriptions.findByCustomerIdAndShopId(id, SHOP).orElseThrow().getId();

        accounts.unfollow(id, SHOP);
        assertTrue(accounts.followedShops(id).isEmpty());

        accounts.follow(id, SHOP);
        assertEquals(1, subscriptions.findByCustomerId(id).size());
        assertEquals(rowBefore, subscriptions.findByCustomerIdAndShopId(id, SHOP).orElseThrow().getId());
        assertEquals(1, accounts.followedShops(id).size());
    }

    @Test
    void unfollowWithoutARowIsNoOp() {
        Long id = accounts.create("Asha");
        accounts.unfollow(id, SHOP);
        assertEquals(0, subscriptions.findByCustomerId(id).size());
    }

    @Test
    void inactiveSubscriptionIsHiddenFromTheList() {
        Long id = accounts.create("Asha");
        Subscription s = new Subscription(id, SHOP);
        s.setActive(false);
        subscriptions.saveAndFlush(s);

        assertFalse(accounts.followedShops(id).stream().anyMatch(f -> f.shopId().equals(SHOP)));
    }

    @Test
    void unknownCustomerIsNotFound() {
        assertThrows(CustomerNotFoundException.class, () -> accounts.followedShops(999_999L));
        assertThrows(CustomerNotFoundException.class, () -> accounts.follow(999_999L, SHOP));
        assertThrows(CustomerNotFoundException.class, () -> accounts.unfollow(999_999L, SHOP));
    }

    @Test
    void unknownShopIsNotFound() {
        Long id = accounts.create("Asha");
        assertThrows(ShopNotFoundException.class, () -> accounts.follow(id, 999_999L));
        assertThrows(ShopNotFoundException.class, () -> accounts.unfollow(id, 999_999L));
    }
}
