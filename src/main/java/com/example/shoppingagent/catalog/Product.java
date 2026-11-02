package com.example.shoppingagent.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** A product in the catalog (our local stand-in for Google Retail Search data). */
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private String brand;
    @Column(length = 500)
    private String description;
    private BigDecimal price;
    /** Discount in percent, 0 if there is no offer. */
    private int discountPercent;
    private boolean inStock;

    protected Product() {}

    public Product(String name, String category, String brand, String description,
                   BigDecimal price, int discountPercent, boolean inStock) {
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.description = description;
        this.price = price;
        this.discountPercent = discountPercent;
        this.inStock = inStock;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getDiscountPercent() { return discountPercent; }
    public boolean isInStock() { return inStock; }
}
