package com.twothreads.bachat.customer.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import com.twothreads.bachat.customer.entity.Customer;
import com.twothreads.bachat.customer.entity.Subscription;

// Migrated schema with validation, like the app (see ShopListingServiceTest for why).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CustomerRepositoryTest {

    @Autowired CustomerRepository customers;
    @Autowired SubscribersRepositories subscriptions;
    @Autowired EntityManager em;

    private void reload() {
        em.flush();
        em.clear(); // read back from the database, so DB-generated columns are filled
    }

    @Test
    void savesCustomerAndReadsCreatedAtFromDatabase() {
        Customer saved = customers.saveAndFlush(new Customer("Asha"));
        reload();

        Customer found = customers.findById(saved.getId()).orElseThrow();

        assertEquals("Asha", found.getName());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void anonymousCustomerHasNoName() {
        Customer saved = customers.saveAndFlush(new Customer());
        reload();
        assertEquals(null, customers.findById(saved.getId()).orElseThrow().getName());
    }

    @Test
    void subscriptionLinksCustomerToSeededShop() {
        Customer customer = customers.saveAndFlush(new Customer("Ravi"));

        Subscription saved = subscriptions.saveAndFlush(new Subscription(customer.getId(), 1L)); // shop 1 is seeded
        reload();

        Subscription found = subscriptions.findById(saved.getId()).orElseThrow();
        assertEquals(customer.getId(), found.getCustomerId());
        assertEquals(1L, found.getShopId());
        assertNotNull(found.getCreatedAt());
    }
}
