package com.example.shoppingagent.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    /** Example: GET /api/products/search?q=tv&maxPrice=800&onlyOffers=true */
    @GetMapping("/search")
    public List<Product> search(@RequestParam(required = false) String q,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) BigDecimal maxPrice,
                                @RequestParam(defaultValue = "false") boolean onlyOffers) {
        return service.search(q, category, maxPrice, onlyOffers);
    }
}
