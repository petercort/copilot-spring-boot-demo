package com.fisglobal.order.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    private OrderItem buildItem(int qty, double unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setProductName("Widget");
        item.setProductSku("SKU-001");
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(unitPrice));
        return item;
    }

    @Test
    void calculateSubtotal_setsCorrectValue() {
        OrderItem item = buildItem(3, 10.00);

        item.calculateSubtotal();

        assertThat(item.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(30.00));
    }

    @Test
    void getSubtotal_computesOnDemandIfNotSet() {
        OrderItem item = buildItem(4, 5.00);
        // subtotal not yet set

        BigDecimal subtotal = item.getSubtotal();

        assertThat(subtotal).isEqualByComparingTo(BigDecimal.valueOf(20.00));
    }

    @Test
    void getSubtotal_returnsAlreadyCalculatedValue() {
        OrderItem item = buildItem(2, 7.00);
        item.calculateSubtotal(); // sets 14.00

        // manually change unitPrice but don't recalculate
        item.setUnitPrice(BigDecimal.valueOf(100.00));

        // getSubtotal should return the already-set value, not recompute
        assertThat(item.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(14.00));
    }

    @Test
    void equals_sameId_returnsTrue() {
        OrderItem a = buildItem(1, 5.00);
        a.setId(1L);
        OrderItem b = buildItem(1, 5.00);
        b.setId(1L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentId_returnsFalse() {
        OrderItem a = buildItem(1, 5.00);
        a.setId(1L);
        OrderItem b = buildItem(1, 5.00);
        b.setId(2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_nullId_returnsFalse() {
        OrderItem a = buildItem(1, 5.00);
        OrderItem b = buildItem(1, 5.00);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCode_consistentWithEquals() {
        OrderItem a = buildItem(1, 5.00);
        a.setId(1L);
        OrderItem b = buildItem(1, 5.00);
        b.setId(1L);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
