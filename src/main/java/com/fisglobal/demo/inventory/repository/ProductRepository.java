package com.fisglobal.demo.inventory.repository;

import com.fisglobal.demo.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Product entity.
 * In a microservices architecture, this would be isolated in the Inventory Service.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findBySku(String sku);
    
    List<Product> findByCategory(String category);
    
    List<Product> findByActiveTrue();
    
    List<Product> findByStockQuantityLessThanEqual(Integer quantity);
    
    boolean existsBySku(String sku);
}
