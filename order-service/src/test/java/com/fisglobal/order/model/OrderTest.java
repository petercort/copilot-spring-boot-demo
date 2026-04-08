package com.fisglobal.order.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private OrderItem buildItem(long productId, int qty, double price) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName("Widget-" + productId);
        item.setProductSku("SKU-" + productId);
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(price));
        return item;
    }

    @Test
    void addItem_appendsItemAndSetsBackReference() {
        Order order = new Order();
        OrderItem item = buildItem(1L, 2, 10.00);

        order.addItem(item);

        assertThat(order.getItems()).containsExactly(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void recalculateTotal_sumsSubtotals() {
        Order order = new Order();
        order.addItem(buildItem(1L, 2, 10.00)); // 20.00
        order.addItem(buildItem(2L, 3, 5.00));  // 15.00

        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(35.00));
    }

    @Test
    void recalculateTotal_emptyItems_returnsZero() {
        Order order = new Order();

        order.recalculateTotal();

        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void onCreate_setsTimestampsAndGeneratesOrderNumber() {
        Order order = new Order();

        order.onCreate();

        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
        assertThat(order.getOrderNumber()).startsWith("ORD-");
    }

    @Test
    void onCreate_doesNotOverwriteExistingOrderNumber() {
        Order order = new Order();
        order.setOrderNumber("ORD-CUSTOM");

        order.onCreate();

        assertThat(order.getOrderNumber()).isEqualTo("ORD-CUSTOM");
    }

    @Test
    void onUpdate_updatesTimestamp() throws InterruptedException {
        Order order = new Order();
        order.onCreate();
        var after = order.getUpdatedAt();

        Thread.sleep(1);
        order.onUpdate();

        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(after);
    }

    @Test
    void equals_sameId_returnsTrue() {
        Order a = new Order();
        a.setId(1L);
        Order b = new Order();
        b.setId(1L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentId_returnsFalse() {
        Order a = new Order();
        a.setId(1L);
        Order b = new Order();
        b.setId(2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_nullId_returnsFalse() {
        Order a = new Order();
        Order b = new Order();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Order a = new Order();
        a.setId(1L);
        Order b = new Order();
        b.setId(1L);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
