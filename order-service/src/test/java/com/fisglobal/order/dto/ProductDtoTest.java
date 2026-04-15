package com.fisglobal.order.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDtoTest {

    private ProductDto build(int stockQuantity) {
        return new ProductDto(1L, "Widget", "Desc", "SKU-001",
                BigDecimal.valueOf(9.99), stockQuantity, "Electronics", 5, true, null, null);
    }

    @Test
    void isInStock_true_whenStockPositive() {
        ProductDto dto = build(5);

        assertThat(dto.isInStock()).isTrue();
    }

    @Test
    void isInStock_false_whenStockZero() {
        ProductDto dto = build(0);

        assertThat(dto.isInStock()).isFalse();
    }

    @Test
    void isInStock_false_whenStockNull() {
        ProductDto dto = new ProductDto(1L, "Widget", "Desc", "SKU-001",
                BigDecimal.valueOf(9.99), null, "Electronics", 5, true, null, null);

        assertThat(dto.isInStock()).isFalse();
    }
}
