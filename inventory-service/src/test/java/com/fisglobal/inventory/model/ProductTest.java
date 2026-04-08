package com.fisglobal.inventory.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    private Product buildProduct(Long id, int stock, Integer reorderLevel) {
        Product p = new Product();
        p.setId(id);
        p.setName("Widget");
        p.setSku("SKU-001");
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStockQuantity(stock);
        p.setCategory("Electronics");
        p.setReorderLevel(reorderLevel);
        p.setActive(true);
        return p;
    }

    @Test
    void isInStock_true_whenStockPositive() {
        Product p = buildProduct(1L, 5, null);

        assertThat(p.isInStock()).isTrue();
    }

    @Test
    void isInStock_false_whenStockZero() {
        Product p = buildProduct(1L, 0, null);

        assertThat(p.isInStock()).isFalse();
    }

    @Test
    void needsReorder_true_whenStockAtOrBelowReorderLevel() {
        Product p = buildProduct(1L, 3, 5);

        assertThat(p.needsReorder()).isTrue();
    }

    @Test
    void needsReorder_false_whenStockAboveReorderLevel() {
        Product p = buildProduct(1L, 10, 5);

        assertThat(p.needsReorder()).isFalse();
    }

    @Test
    void needsReorder_false_whenReorderLevelNull() {
        Product p = buildProduct(1L, 2, null);

        assertThat(p.needsReorder()).isFalse();
    }

    @Test
    void onCreate_setsTimestampsAndDefaultsActive() {
        Product p = new Product();
        p.setName("Test");
        p.setSku("T-1");
        p.setPrice(BigDecimal.ONE);
        p.setStockQuantity(1);
        p.setCategory("Cat");
        p.setActive(null);

        p.onCreate();

        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
        assertThat(p.getActive()).isTrue();
    }

    @Test
    void onUpdate_updatesTimestamp() throws InterruptedException {
        Product p = buildProduct(null, 5, null);
        p.onCreate();
        var after = p.getUpdatedAt();

        Thread.sleep(1);
        p.onUpdate();

        assertThat(p.getUpdatedAt()).isAfterOrEqualTo(after);
    }

    @Test
    void equals_sameId_returnsTrue() {
        Product a = buildProduct(1L, 5, null);
        Product b = buildProduct(1L, 5, null);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentId_returnsFalse() {
        Product a = buildProduct(1L, 5, null);
        Product b = buildProduct(2L, 5, null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_nullId_returnsFalse() {
        Product a = buildProduct(null, 5, null);
        Product b = buildProduct(null, 5, null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Product a = buildProduct(1L, 5, null);
        Product b = buildProduct(1L, 5, null);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
