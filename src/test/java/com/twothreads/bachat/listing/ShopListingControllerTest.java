package com.twothreads.bachat.listing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.twothreads.bachat.config.ApiExceptionHandler;
import com.twothreads.bachat.listing.ListingDto.CreateResponse;
import com.twothreads.bachat.listing.ListingDto.ListingView;
import com.twothreads.bachat.listing.ListingDto.ListingsResponse;
import com.twothreads.bachat.listing.ListingDto.ParseResponse;
import com.twothreads.bachat.listing.ListingDto.ParsedLineView;
import com.twothreads.bachat.listing.ListingDto.ShopView;
import com.twothreads.bachat.listing.ListingDto.SoldOutResponse;

@WebMvcTest(ShopListingController.class)
@Import(ApiExceptionHandler.class)
class ShopListingControllerTest {

    private static final String LISTINGS = "/api/shops/1/listings";

    @Autowired MockMvc mvc;
    @MockitoBean ShopListingService service;

    private static final String MILK_LINE = """
            {"rawPhrase": "doodh 6 packet original price 30 discounted price 23",
             "itemKey": "MILK", "itemName": "Milk", "quantity": 6, "unit": "packet",
             "offerPrice": 23.00, "originalPrice": 30.00, "sellToday": false}
            """;

    @Test
    void parseReturnsLines() throws Exception {
        ParsedLineView line = new ParsedLineView(
                "doodh 6 packet original price 30 discounted price 23",
                "MILK", "Milk", 6, "packet", new BigDecimal("23.00"), new BigDecimal("30.00"), List.of());
        when(service.parse(anyLong(), any())).thenReturn(new ParseResponse(List.of(line), false));

        mvc.perform(post(LISTINGS + "/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\": \"doodh 6 packet original price 30 discounted price 23\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].itemKey").value("MILK"))
                .andExpect(jsonPath("$.lines[0].offerPrice").value(23.00))
                .andExpect(jsonPath("$.blocking").value(false));
    }

    @Test
    void parseRejectsBlankTranscript() throws Exception {
        mvc.perform(post(LISTINGS + "/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("transcript is required"));
    }

    @Test
    void parseUnknownShopIs404() throws Exception {
        when(service.parse(anyLong(), any())).thenThrow(new ShopNotFoundException(99L));

        mvc.perform(post("/api/shops/99/listings/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\": \"doodh 6 packet original price 30 discounted price 23\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("shop not found"));
    }

    @Test
    void createReturns201WithIds() throws Exception {
        when(service.create(anyLong(), any())).thenReturn(new CreateResponse(2, List.of(101L, 102L)));

        mvc.perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\": [" + MILK_LINE + "," + MILK_LINE + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.listingIds[0]").value(101));
    }

    @Test
    void createBlockingLineIs422NamingTheLine() throws Exception {
        when(service.create(anyLong(), any())).thenThrow(
                new ListingValidationException("line 2 has a blocking issue: PRICE_ABOVE_ORIGINAL"));

        mvc.perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\": [" + MILK_LINE + "," + MILK_LINE + "]}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("line 2 has a blocking issue: PRICE_ABOVE_ORIGINAL"));
    }

    @Test
    void createWithoutLinesIs400() throws Exception {
        mvc.perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("lines is required"));
    }

    @Test
    void createWithTooManyLinesIs400() throws Exception {
        String lines = String.join(",", java.util.Collections.nCopies(51, MILK_LINE));

        mvc.perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\": [" + lines + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("too many lines"));
    }

    @Test
    void listReturnsDiscountPercent() throws Exception {
        ListingView view = new ListingView(101L, "Milk", 6, "packet",
                new BigDecimal("23.00"), new BigDecimal("30.00"), 23, "2026-11-15T21:00:00+05:30");
        when(service.listActive(anyLong())).thenReturn(new ListingsResponse(
                new ShopView(1L, "Sharma General Store", "21:00"), List.of(view)));

        mvc.perform(get(LISTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.name").value("Sharma General Store"))
                .andExpect(jsonPath("$.listings[0].discountPercent").value(23))
                .andExpect(jsonPath("$.listings[0].endsAt").value("2026-11-15T21:00:00+05:30"));
    }

    @Test
    void soldOutReturnsCount() throws Exception {
        when(service.markAllSoldOut(anyLong())).thenReturn(new SoldOutResponse(2));

        mvc.perform(post(LISTINGS + "/sold-out"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
    }
}
