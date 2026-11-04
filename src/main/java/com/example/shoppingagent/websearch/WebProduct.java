package com.example.shoppingagent.websearch;

import java.math.BigDecimal;
import java.util.List;

/**
 * One Google Shopping result, trimmed to what the assistant needs.
 * (The raw SerpApi item also carries huge tokens and image URLs that would only waste LLM tokens.)
 *
 * @param price  numeric price in dollars, or null if the result had none
 * @param notes  extra info such as "Nearby, 3 mi" or "Delivery within 1h $20"
 */
public record WebProduct(String title, BigDecimal price, String store, Double rating,
                         Integer reviews, String link, List<String> notes) {}
