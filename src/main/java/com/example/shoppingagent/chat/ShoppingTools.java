package com.example.shoppingagent.chat;

import com.example.shoppingagent.catalog.CatalogService;
import com.example.shoppingagent.catalog.Product;
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

    private final CatalogService catalog;

    public ShoppingTools(CatalogService catalog) {
        this.catalog = catalog;
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
        return List.of(Map.of("functionDeclarations", List.of(searchProducts)));
    }

    /** Runs a tool and returns a JSON-friendly result. Failures become {"error": ...} so the model can recover. */
    public Map<String, Object> execute(String name, JsonNode args) {
        if (!SEARCH_PRODUCTS.equals(name)) {
            return Map.of("error", "Unknown tool: " + name);
        }
        try {
            return searchProducts(args);
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
