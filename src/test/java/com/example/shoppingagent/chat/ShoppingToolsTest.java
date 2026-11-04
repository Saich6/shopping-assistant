package com.example.shoppingagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shoppingagent.catalog.CatalogService;
import com.example.shoppingagent.catalog.Product;
import com.example.shoppingagent.websearch.SerpApiClient;
import com.example.shoppingagent.websearch.WebProduct;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShoppingToolsTest {
    final CatalogService catalog = mock(CatalogService.class);
    final SerpApiClient serpApi = mock(SerpApiClient.class);
    final ShoppingTools tools = new ShoppingTools(catalog, serpApi);
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
    void toolsPayloadDescribesBothTools() {
        assertThat(tools.toolsPayload().toString())
                .contains("search_products", "only_offers", "max_price", "search_web_products");
    }

    @Test
    @SuppressWarnings("unchecked")
    void webSearchReturnsTrimmedThirdPartyProducts() throws Exception {
        when(serpApi.searchShopping("olive oil")).thenReturn(List.of(new WebProduct(
                "Pompeian Olive Oil", new BigDecimal("12.97"), "Walmart", 4.6, 2300,
                "https://example/p", List.of("Nearby, 3 mi"))));

        Map<String, Object> result = tools.execute("search_web_products", om.readTree("{\"query\":\"olive oil\"}"));

        assertThat(result.get("source").toString()).contains("third-party");
        Map<String, Object> first = ((List<Map<String, Object>>) result.get("products")).get(0);
        assertThat(first).containsEntry("store", "Walmart").containsEntry("name", "Pompeian Olive Oil");
    }

    @Test
    void webSearchFailureBecomesToolErrorNotCrash() throws Exception {
        when(serpApi.searchShopping("tv")).thenThrow(new IllegalStateException("SERPAPI_API_KEY is not set"));
        assertThat(tools.execute("search_web_products", om.readTree("{\"query\":\"tv\"}")))
                .containsKey("error");
    }
}
