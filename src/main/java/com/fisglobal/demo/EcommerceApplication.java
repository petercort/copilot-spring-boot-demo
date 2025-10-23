package com.fisglobal.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the E-Commerce Monolith.
 * This application demonstrates a monolithic architecture that combines
 * multiple business domains (Orders, Customers, Inventory) in a single application.
 */
@SpringBootApplication
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
