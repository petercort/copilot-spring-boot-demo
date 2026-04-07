package com.fisglobal.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Boundary DTO representing a product record received from the Inventory Service.
 * The Order Service never imports Product entity classes — all cross-domain
 * data is exchanged through these DTOs.
 */
public record ProductDto(
    Long id,
    String name,
    String description,
    String sku,
    BigDecimal price,
    Integer stockQuantity,
    String category,
    Integer reorderLevel,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }
}
