package com.twothreads.bachat.customer.repositories;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.twothreads.bachat.customer.entity.Subscription;

@Repository
public interface SubscribersRepositories extends JpaRepository<Subscription, Long> {

    List<Subscription> findByCustomerId(Long customerId);
    List<Subscription> findByActiveTrueAndCustomerId(Long customerId);
}