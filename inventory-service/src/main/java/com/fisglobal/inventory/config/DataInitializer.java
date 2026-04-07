package com.fisglobal.inventory.config;

import com.fisglobal.inventory.model.Product;
import com.fisglobal.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initProducts(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() > 0) {
                return;
            }
            log.info("Seeding product data...");

            Product p1 = new Product();
            p1.setName("Laptop Computer");
            p1.setDescription("High-performance laptop for business and gaming");
            p1.setSku("LAPTOP-001");
            p1.setPrice(new BigDecimal("1299.99"));
            p1.setStockQuantity(50);
            p1.setCategory("Electronics");
            p1.setReorderLevel(10);
            productRepository.save(p1);

            Product p2 = new Product();
            p2.setName("Wireless Mouse");
            p2.setDescription("Ergonomic wireless mouse with long battery life");
            p2.setSku("MOUSE-001");
            p2.setPrice(new BigDecimal("29.99"));
            p2.setStockQuantity(200);
            p2.setCategory("Electronics");
            p2.setReorderLevel(20);
            productRepository.save(p2);

            Product p3 = new Product();
            p3.setName("Mechanical Keyboard");
            p3.setDescription("RGB mechanical keyboard with cherry switches");
            p3.setSku("KEYBOARD-001");
            p3.setPrice(new BigDecimal("149.99"));
            p3.setStockQuantity(75);
            p3.setCategory("Electronics");
            p3.setReorderLevel(15);
            productRepository.save(p3);

            Product p4 = new Product();
            p4.setName("Office Chair");
            p4.setDescription("Ergonomic office chair with lumbar support");
            p4.setSku("CHAIR-001");
            p4.setPrice(new BigDecimal("299.99"));
            p4.setStockQuantity(30);
            p4.setCategory("Furniture");
            p4.setReorderLevel(5);
            productRepository.save(p4);

            Product p5 = new Product();
            p5.setName("Standing Desk");
            p5.setDescription("Adjustable height standing desk");
            p5.setSku("DESK-001");
            p5.setPrice(new BigDecimal("599.99"));
            p5.setStockQuantity(20);
            p5.setCategory("Furniture");
            p5.setReorderLevel(3);
            productRepository.save(p5);

            Product p6 = new Product();
            p6.setName("Webcam HD");
            p6.setDescription("1080p webcam with built-in microphone");
            p6.setSku("WEBCAM-001");
            p6.setPrice(new BigDecimal("79.99"));
            p6.setStockQuantity(100);
            p6.setCategory("Electronics");
            p6.setReorderLevel(10);
            productRepository.save(p6);

            log.info("Seeded {} products", productRepository.count());
        };
    }
}
