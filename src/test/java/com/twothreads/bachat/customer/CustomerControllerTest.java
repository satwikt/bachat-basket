package com.twothreads.bachat.customer;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.twothreads.bachat.config.ApiExceptionHandler;
import com.twothreads.bachat.customer.service.CustomerAccountService;
import com.twothreads.bachat.customer.service.CustomerService;
import com.twothreads.bachat.dto.FollowedShopDto;

@WebMvcTest(CustomerController.class)
@Import(ApiExceptionHandler.class)
class CustomerControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CustomerAccountService accounts;
    @MockitoBean CustomerService customerService;

    @Test
    void createReturns201WithId() throws Exception {
        when(accounts.create(anyString())).thenReturn(7L);

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Asha\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(7));
    }

    @Test
    void createAcceptsNoName() throws Exception {
        when(accounts.create(null)).thenReturn(8L);

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\": null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(8));
    }

    @Test
    void createRejectsLongName() throws Exception {
        String longName = "x".repeat(151);

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name too long"));
    }

    @Test
    void subscriptionsListsFollowedShops() throws Exception {
        when(accounts.followedShops(3L)).thenReturn(List.of(new FollowedShopDto(1L, "Sharma General Store")));

        mvc.perform(get("/api/customers/3/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shopId").value(1))
                .andExpect(jsonPath("$[0].shopName").value("Sharma General Store"));
    }

    @Test
    void followAndUnfollowReturn204() throws Exception {
        mvc.perform(put("/api/customers/3/subscriptions/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/customers/3/subscriptions/1")).andExpect(status().isNoContent());
    }

    @Test
    void unknownCustomerIsNotFound() throws Exception {
        doThrow(new CustomerNotFoundException(99L)).when(accounts).followedShops(anyLong());

        mvc.perform(get("/api/customers/99/subscriptions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("customer not found"));
    }

    @Test
    void offersAreGroupedByShop() throws Exception {
        when(customerService.getListingsByShop(3L)).thenReturn(java.util.Map.of(1L, List.of()));

        mvc.perform(get("/api/customers/3/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.1").isArray());
    }
}
