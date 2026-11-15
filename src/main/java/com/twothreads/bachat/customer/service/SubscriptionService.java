package com.twothreads.bachat.customer.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twothreads.bachat.customer.repositories.SubscribersRepositories;
import com.twothreads.bachat.dto.SubscriptionDto;

@Service
public class SubscriptionService {

    private final SubscribersRepositories repo;

    public SubscriptionService(SubscribersRepositories repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> customerSubscribers(Long custId) {
        return repo.findByCustomerId(custId).stream().map(SubscriptionDto::from).toList();
    }
}
