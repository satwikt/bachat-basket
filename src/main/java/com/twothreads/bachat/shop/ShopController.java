package com.twothreads.bachat.shop;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twothreads.bachat.dto.ShopDto;

/** All shops, for browsing and following. */
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    private final ShopRepository shops;

    public ShopController(ShopRepository shops) {
        this.shops = shops;
    }

    @GetMapping
    public List<ShopDto> all() {
        return shops.findAll().stream()
                .sorted(java.util.Comparator.comparing(Shop::getId))
                .map(ShopDto::from)
                .toList();
    }
}
