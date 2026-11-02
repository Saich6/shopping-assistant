package com.example.shoppingagent.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** All filters are optional: pass null to ignore one. */
    @Query("""
        select p from Product p
        where (:q is null
               or lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.description) like lower(concat('%', :q, '%'))
               or lower(p.brand) like lower(concat('%', :q, '%')))
          and (:category is null or lower(p.category) = lower(:category))
          and (:maxPrice is null or p.price <= :maxPrice)
          and (:onlyOffers = false or p.discountPercent > 0)
        order by p.discountPercent desc, p.price asc
        """)
    List<Product> search(@Param("q") String q,
                         @Param("category") String category,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("onlyOffers") boolean onlyOffers);
}
