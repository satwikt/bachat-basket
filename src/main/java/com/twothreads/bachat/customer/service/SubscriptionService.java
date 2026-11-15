package com.twothreads.bachat.customer.service;
import java.util.List;

import org.springframework.stereotype.Service;

import com.twothreads.bachat.customer.entity.Subscription;
import com.twothreads.bachat.customer.repositories.SubscribersRepositories;


@Service
public class SubscriptionService {
    private final SubscribersRepositories repo;
    public SubscriptionService(SubscribersRepositories repo) { this.repo = repo; }

    public List<Subscription> customerSubscribers( Long custId) {
        return repo.findByCustomerId(custId);
    }
}