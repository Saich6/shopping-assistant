package com.example.shoppingagent.chat;

import com.example.shoppingagent.catalog.CatalogService;
import com.example.shoppingagent.catalog.Product;
import com.example.shoppingagent.websearch.SerpApiClient;
import com.example.shoppingagent.websearch.WebProduct;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The tools Gemini is allowed to call. Two jobs:
 *  1. describe each tool to Gemini (name, purpose, parameters), see {@link #toolsPayload()};
 *  2. run a tool when Gemini asks for it, see {@link #execute(String, JsonNode)}.
 */
@Component
public class ShoppingTools {
    public static final String SEARCH_PRODUCTS = "search_products";
    public static final String SEARCH_WEB_PRODUCTS = "search_web_products";

    private final CatalogService catalog;
    private final SerpApiClient serpApi;

    public ShoppingTools(CatalogService catalog, SerpApiClient serpApi) {
        this.catalog = catalog;
        this.serpApi = serpApi;
    }

    /** The "tools" section of the Gemini request: a menu of functions the model may call. */
    public List<Map<String, Object>> toolsPayload() {
        Map<String, Object> searchProducts = Map.of(
                "name", SEARCH_PRODUCTS,
                "description", "Search the store catalog for products. Use it for any question about "
                        + "products, prices, stock or offers. Returns up to 10 matching products.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description",
                                        "One short keyword in singular form, e.g. 'tv', 'headphone', 'olive oil'. "
                                                + "Omit to browse by category or offers."),
                                "category", Map.of("type", "string", "description",
                                        "One of: electronics, home, kitchen, grocery, furniture, tires, outdoor."),
                                "max_price", Map.of("type", "number", "description",
                                        "Maximum price in dollars."),
                                "only_offers", Map.of("type", "boolean", "description",
                                        "true to return only products that are on offer (discounted)."))));

        Map<String, Object> searchWebProducts = Map.of(
                "name", SEARCH_WEB_PRODUCTS,
                "description", "Search the wider web (Google Shopping) for products sold by many third-party "
                        + "stores. Use it ONLY when search_products found nothing suitable, or when the customer "
                        + "asks for a wider selection, other brands, or comparisons. Slower than search_products. "
                        + "Results are third-party listings, NOT this store's inventory or prices.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description",
                                        "A natural product search phrase, e.g. 'organic olive oil 2L'."))));
        return List.of(Map.of("functionDeclarations", List.of(searchProducts, searchWebProducts)));
    }

    /** Runs a tool and returns a JSON-friendly result. Failures become {"error": ...} so the model can recover. */
    public Map<String, Object> execute(String name, JsonNode args) {
        try {
            return switch (name) {
                case SEARCH_PRODUCTS -> searchProducts(args);
                case SEARCH_WEB_PRODUCTS -> searchWebProducts(args);
                default -> Map.of("error", "Unknown tool: " + name);
            };
        } catch (RuntimeException e) {
            return Map.of("error", "Tool failed: " + e.getMessage());
        }
    }

    private Map<String, Object> searchProducts(JsonNode args) {
        String query = args.path("query").asText(null);
        String category = args.path("category").asText(null);
        BigDecimal maxPrice = toBigDecimal(args.path("max_price"));
        boolean onlyOffers = args.path("only_offers").asBoolean(false);

        List<Map<String, Object>> products = catalog.search(query, category, maxPrice, onlyOffers)
                .stream().map(ShoppingTools::toMap).toList();
        return Map.of("count", products.size(), "products", products);
    }

    private Map<String, Object> searchWebProducts(JsonNode args) {
        List<Map<String, Object>> products = serpApi.searchShopping(args.path("query").asText(""))
                .stream().map(ShoppingTools::toMap).toList();
        return Map.of(
                "source", "third-party web results (Google Shopping), not this store's inventory",
                "count", products.size(),
                "products", products);
    }

    private static Map<String, Object> toMap(WebProduct p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.title());
        m.put("price", p.price());
        m.put("store", p.store());
        m.put("rating", p.rating());
        m.put("reviews", p.reviews());
        m.put("link", p.link());
        m.put("notes", p.notes());
        return m;
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
    }

    private static Map<String, Object> toMap(Product p) {
        BigDecimal salePrice = p.getPrice()
                .multiply(BigDecimal.valueOf(100 - p.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("brand", p.getBrand());
        m.put("category", p.getCategory());
        m.put("description", p.getDescription());
        m.put("price", p.getPrice());
        m.put("discount_percent", p.getDiscountPercent());
        m.put("sale_price", salePrice);
        m.put("in_stock", p.isInStock());
        return m;
    }
}
