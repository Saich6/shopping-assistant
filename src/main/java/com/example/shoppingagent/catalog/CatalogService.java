package com.example.shoppingagent.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private static final int MAX_RESULTS = 10;

    private final ProductRepository repository;

    public CatalogService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> search(String query, String category, BigDecimal maxPrice, boolean onlyOffers) {
        return repository.search(blankToNull(query), blankToNull(category), maxPrice, onlyOffers)
                .stream().limit(MAX_RESULTS).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
