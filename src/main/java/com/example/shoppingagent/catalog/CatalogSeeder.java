package com.example.shoppingagent.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Loads a small set of fake products at startup. */
@Component
public class CatalogSeeder implements CommandLineRunner {
    private final ProductRepository repository;

    public CatalogSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;
        List<Product> p = new ArrayList<>();
        p.add(new Product("Samsung 65\" 4K QLED Smart TV", "electronics", "Samsung", "65 inch 4K QLED television with smart apps and HDR", bd("899.99"), 15, true));
        p.add(new Product("LG 55\" 4K OLED TV", "electronics", "LG", "55 inch OLED television with deep blacks and webOS", bd("1199.99"), 0, true));
        p.add(new Product("Sony Noise Cancelling Headphones", "electronics", "Sony", "Wireless over-ear headphones with 30 hour battery", bd("279.99"), 20, true));
        p.add(new Product("Apple AirPods Pro", "electronics", "Apple", "Wireless earbuds with active noise cancellation", bd("229.99"), 10, true));
        p.add(new Product("Dyson Cordless Vacuum", "home", "Dyson", "Lightweight cordless stick vacuum for carpets and hard floors", bd("449.99"), 25, true));
        p.add(new Product("Instant Pot Duo 6 Quart", "kitchen", "Instant Pot", "7-in-1 electric pressure cooker", bd("89.99"), 30, true));
        p.add(new Product("KitchenAid Stand Mixer", "kitchen", "KitchenAid", "5 quart tilt-head stand mixer for baking", bd("329.99"), 0, true));
        p.add(new Product("Kirkland Signature Olive Oil 2L", "grocery", "Kirkland Signature", "Extra virgin olive oil, cold pressed", bd("21.99"), 0, true));
        p.add(new Product("Kirkland Signature Organic Eggs 24ct", "grocery", "Kirkland Signature", "Organic large eggs, 24 count", bd("8.99"), 0, true));
        p.add(new Product("Kirkland Signature Whey Protein", "grocery", "Kirkland Signature", "Chocolate whey protein powder, 4 lb", bd("39.99"), 10, true));
        p.add(new Product("Tempur-Pedic Queen Mattress", "furniture", "Tempur-Pedic", "Memory foam queen mattress with cooling cover", bd("1599.99"), 12, true));
        p.add(new Product("Ergonomic Office Chair", "furniture", "Hon", "Mesh back office chair with lumbar support", bd("199.99"), 0, false));
        p.add(new Product("Michelin All-Season Tire 225/65R17", "tires", "Michelin", "All-season passenger tire, 4 tire set eligible", bd("159.99"), 0, true));
        p.add(new Product("Weber Gas Grill 3 Burner", "outdoor", "Weber", "Three burner propane gas grill with side tables", bd("499.99"), 18, true));
        p.add(new Product("Dell 15\" Laptop", "electronics", "Dell", "15 inch laptop, 16GB RAM, 512GB SSD", bd("749.99"), 8, true));
        repository.saveAll(p);
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
