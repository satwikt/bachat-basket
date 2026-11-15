package com.twothreads.bachat.customer;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.twothreads.bachat.customer.service.CustomerAccountService;
import com.twothreads.bachat.customer.service.CustomerService;
import com.twothreads.bachat.dto.FollowedShopDto;
import com.twothreads.bachat.dto.ListingDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Anonymous customers, follow/unfollow, and the offers from followed shops. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    public record CreateCustomerRequest(
            @Size(max = 150, message = "name too long") String name) {
    }

    public record CreateCustomerResponse(Long customerId) {
    }

    private final CustomerAccountService accounts;
    private final CustomerService customerService;

    public CustomerController(CustomerAccountService accounts, CustomerService customerService) {
        this.accounts = accounts;
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return new CreateCustomerResponse(accounts.create(request.name()));
    }

    @GetMapping("/{customerId}/subscriptions")
    public List<FollowedShopDto> subscriptions(@PathVariable Long customerId) {
        return accounts.followedShops(customerId);
    }

    @PutMapping("/{customerId}/subscriptions/{shopId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@PathVariable Long customerId, @PathVariable Long shopId) {
        accounts.follow(customerId, shopId);
    }

    @DeleteMapping("/{customerId}/subscriptions/{shopId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable Long customerId, @PathVariable Long shopId) {
        accounts.unfollow(customerId, shopId);
    }

    /** Active listings from every followed shop, keyed by shop id. */
    @GetMapping("/{customerId}/offers")
    public Map<Long, List<ListingDto>> offers(@PathVariable Long customerId) {
        accounts.requireCustomer(customerId);
        return customerService.getListingsByShop(customerId);
    }
}
