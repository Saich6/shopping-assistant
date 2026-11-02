package com.example.shoppingagent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CatalogServiceTest {
    @Autowired CatalogService service;

    @Test
    void findsByKeyword() {
        assertThat(service.search("tv", null, null, false))
                .extracting(Product::getName).anyMatch(n -> n.contains("TV"));
    }

    @Test
    void respectsMaxPrice() {
        assertThat(service.search(null, "electronics", new BigDecimal("300"), false))
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("300")) <= 0)
                .isNotEmpty();
    }

    @Test
    void onlyOffersReturnsDiscountedItems() {
        assertThat(service.search(null, null, null, true))
                .isNotEmpty().allMatch(p -> p.getDiscountPercent() > 0);
    }
}
