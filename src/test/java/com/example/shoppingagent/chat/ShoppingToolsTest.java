package com.example.shoppingagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shoppingagent.catalog.CatalogService;
import com.example.shoppingagent.catalog.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShoppingToolsTest {
    final CatalogService catalog = mock(CatalogService.class);
    final ShoppingTools tools = new ShoppingTools(catalog);
    final ObjectMapper om = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void searchPassesArgumentsToCatalogAndComputesSalePrice() throws Exception {
        when(catalog.search("tv", null, new BigDecimal("1000"), true)).thenReturn(List.of(
                new Product("Samsung TV", "electronics", "Samsung", "desc", new BigDecimal("899.99"), 15, true)));

        Map<String, Object> result = tools.execute("search_products",
                om.readTree("{\"query\":\"tv\",\"max_price\":1000,\"only_offers\":true}"));

        verify(catalog).search("tv", null, new BigDecimal("1000"), true);
        assertThat(result.get("count")).isEqualTo(1);
        Map<String, Object> first = ((List<Map<String, Object>>) result.get("products")).get(0);
        assertThat(first.get("name")).isEqualTo("Samsung TV");
        assertThat(first.get("sale_price")).isEqualTo(new BigDecimal("764.99")); // 899.99 * 0.85
    }

    @Test
    void emptyArgsMeansNoFilters() throws Exception {
        tools.execute("search_products", om.readTree("{}"));
        verify(catalog).search(null, null, null, false);
    }

    @Test
    void unknownToolReturnsErrorInsteadOfThrowing() throws Exception {
        assertThat(tools.execute("delete_everything", om.readTree("{}"))).containsKey("error");
    }

    @Test
    void toolsPayloadDescribesSearchProducts() {
        assertThat(tools.toolsPayload().toString()).contains("search_products", "only_offers", "max_price");
    }
}
