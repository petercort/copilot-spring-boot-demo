package com.fisglobal.inventory.service;

import com.fisglobal.inventory.model.Product;
import com.fisglobal.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        log.debug("Fetching all products");
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts() {
        log.debug("Fetching active products");
        return productRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        log.debug("Fetching product with id: {}", id);
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductBySku(String sku) {
        log.debug("Fetching product with SKU: {}", sku);
        return productRepository.findBySku(sku);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        log.debug("Fetching products in category: {}", category);
        return productRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts(Integer threshold) {
        log.debug("Fetching products with stock <= {}", threshold);
        return productRepository.findByStockQuantityLessThanEqual(threshold);
    }

    @Transactional
    public Product createProduct(Product product) {
        log.debug("Creating new product: {}", product.getSku());

        if (productRepository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("Product with SKU " + product.getSku() + " already exists");
        }

        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, Product productDetails) {
        log.debug("Updating product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setSku(productDetails.getSku());
        product.setPrice(productDetails.getPrice());
        product.setStockQuantity(productDetails.getStockQuantity());
        product.setCategory(productDetails.getCategory());
        product.setReorderLevel(productDetails.getReorderLevel());
        product.setActive(productDetails.getActive());

        return productRepository.save(product);
    }

    @Transactional
    public boolean reserveStock(Long productId, Integer quantity) {
        log.debug("Reserving {} units of product {}", quantity, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        if (product.getStockQuantity() < quantity) {
            log.warn("Insufficient stock for product {}. Available: {}, Requested: {}",
                    productId, product.getStockQuantity(), quantity);
            return false;
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        log.info("Reserved {} units of product {}", quantity, productId);
        return true;
    }

    @Transactional
    public void restoreStock(Long productId, Integer quantity) {
        log.debug("Restoring {} units of product {}", quantity, productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);

        log.info("Restored {} units of product {}", quantity, productId);
    }

    @Transactional
    public void deleteProduct(Long id) {
        log.debug("Deleting product with id: {}", id);

        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Product not found with id: " + id);
        }

        productRepository.deleteById(id);
    }
}
