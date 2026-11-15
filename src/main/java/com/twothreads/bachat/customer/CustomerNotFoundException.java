package com.twothreads.bachat.customer;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("customer " + customerId + " not found");
    }
}
