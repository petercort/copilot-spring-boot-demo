package com.fisglobal.order.service;

import com.fisglobal.order.dto.CreateOrderRequest;
import com.fisglobal.order.dto.CustomerDto;
import com.fisglobal.order.dto.OrderItemRequest;
import com.fisglobal.order.dto.ProductDto;
import com.fisglobal.order.model.Order;
import com.fisglobal.order.model.OrderItem;
import com.fisglobal.order.port.CustomerServicePort;
import com.fisglobal.order.port.InventoryServicePort;
import com.fisglobal.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    CustomerServicePort customerServicePort;

    @Mock
    InventoryServicePort inventoryServicePort;

    @InjectMocks
    OrderService orderService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CustomerDto customerDto(long id) {
        return new CustomerDto(id, "Jane", "Doe", "jane@example.com", "555-0000",
                "1 Main St", "Springfield", "IL", "62701", "US", null, null);
    }

    private ProductDto productDto(long id, int stock) {
        return new ProductDto(id, "Widget-" + id, "Desc", "SKU-" + id,
                BigDecimal.valueOf(10.00), stock, "Electronics", 5, true, null, null);
    }

    private Order savedOrder(long id) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNumber("ORD-" + id);
        o.setStatus(Order.OrderStatus.CONFIRMED);
        o.setCustomerId(1L);
        return o;
    }

    private OrderItem item(long productId, int qty) {
        OrderItem i = new OrderItem();
        i.setProductId(productId);
        i.setProductName("Widget-" + productId);
        i.setProductSku("SKU-" + productId);
        i.setQuantity(qty);
        i.setUnitPrice(BigDecimal.valueOf(10.00));
        return i;
    }

    // ── Read-only queries ─────────────────────────────────────────────────────

    @Test
    void getAllOrders_returnsList() {
        when(orderRepository.findAll()).thenReturn(List.of(savedOrder(1L)));

        List<Order> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        verify(orderRepository).findAll();
    }

    @Test
    void getOrderById_found() {
        Order order = savedOrder(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.getOrderById(1L);

        assertThat(result).contains(order);
    }

    @Test
    void getOrderById_notFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Order> result = orderService.getOrderById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getOrderByOrderNumber_returnsOrder() {
        Order order = savedOrder(1L);
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.getOrderByOrderNumber("ORD-1");

        assertThat(result).contains(order);
    }

    @Test
    void getOrdersByCustomerId_returnsList() {
        when(orderRepository.findByCustomerId(1L)).thenReturn(List.of(savedOrder(1L)));

        List<Order> result = orderService.getOrdersByCustomerId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getOrdersByStatus_returnsList() {
        when(orderRepository.findByStatus(Order.OrderStatus.CONFIRMED))
                .thenReturn(List.of(savedOrder(1L)));

        List<Order> result = orderService.getOrdersByStatus(Order.OrderStatus.CONFIRMED);

        assertThat(result).hasSize(1);
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_success() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(new OrderItemRequest(10L, 2)),
                "1 Main St", "Springfield", "IL", "62701", "US");

        when(customerServicePort.findById(1L)).thenReturn(Optional.of(customerDto(1L)));
        when(inventoryServicePort.findProductById(10L)).thenReturn(Optional.of(productDto(10L, 20)));
        when(inventoryServicePort.reserveStock(10L, 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createOrder(request);

        assertThat(result.getCustomerId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(result.getItems()).hasSize(1);
        verify(inventoryServicePort).reserveStock(10L, 2);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_customerNotFound_throwsIllegalArgument() {
        CreateOrderRequest request = new CreateOrderRequest(
                99L, List.of(new OrderItemRequest(10L, 1)), null, null, null, null, null);

        when(customerServicePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(inventoryServicePort, never()).reserveStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_productNotFound_throwsAndNoCompensation() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(new OrderItemRequest(99L, 1)), null, null, null, null, null);

        when(customerServicePort.findById(1L)).thenReturn(Optional.of(customerDto(1L)));
        when(inventoryServicePort.findProductById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        // nothing was reserved, so no compensation needed
        verify(inventoryServicePort, never()).restoreStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_outOfStockProduct_throwsIllegalArgument() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(new OrderItemRequest(10L, 1)), null, null, null, null, null);

        when(customerServicePort.findById(1L)).thenReturn(Optional.of(customerDto(1L)));
        // Product has zero stock — isInStock() returns false
        when(inventoryServicePort.findProductById(10L)).thenReturn(Optional.of(productDto(10L, 0)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of stock");

        verify(inventoryServicePort, never()).reserveStock(any(), any());
        verify(inventoryServicePort, never()).restoreStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_insufficientStock_throwsIllegalArgument() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(new OrderItemRequest(10L, 5)), null, null, null, null, null);

        when(customerServicePort.findById(1L)).thenReturn(Optional.of(customerDto(1L)));
        when(inventoryServicePort.findProductById(10L)).thenReturn(Optional.of(productDto(10L, 10)));
        when(inventoryServicePort.reserveStock(10L, 5)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");

        // reserveStock returned false — nothing was reserved, no compensation
        verify(inventoryServicePort, never()).restoreStock(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_partialReservation_compensatesAndThrows() {
        // item1 reserves OK, item2 product not found → compensation restores item1
        OrderItemRequest item1Request = new OrderItemRequest(10L, 2);
        OrderItemRequest item2Request = new OrderItemRequest(20L, 1);
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(item1Request, item2Request), null, null, null, null, null);

        when(customerServicePort.findById(1L)).thenReturn(Optional.of(customerDto(1L)));
        when(inventoryServicePort.findProductById(10L)).thenReturn(Optional.of(productDto(10L, 10)));
        when(inventoryServicePort.reserveStock(10L, 2)).thenReturn(true);
        when(inventoryServicePort.findProductById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class);

        // item1's reservation must be compensated
        verify(inventoryServicePort).restoreStock(10L, 2);
        verify(orderRepository, never()).save(any());
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    void updateOrderStatus_alreadyCancelled_noInventoryRestore() {
        Order order = savedOrder(1L);
        order.setStatus(Order.OrderStatus.CANCELLED);
        order.getItems().add(item(10L, 2));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED);

        // was already CANCELLED — the condition oldStatus != CANCELLED is false, skip restore
        verify(inventoryServicePort, never()).restoreStock(any(), any());
    }

    @Test
    void updateOrderStatus_success() {
        Order order = savedOrder(1L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.PROCESSING);

        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PROCESSING);
        verify(inventoryServicePort, never()).restoreStock(any(), any());
    }

    @Test
    void updateOrderStatus_toCancelled_restoresAllItemStocks() {
        Order order = savedOrder(1L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.getItems().add(item(10L, 3));
        order.getItems().add(item(20L, 1));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED);

        verify(inventoryServicePort).restoreStock(10L, 3);
        verify(inventoryServicePort).restoreStock(20L, 1);
    }

    @Test
    void updateOrderStatus_orderNotFound_throwsIllegalArgument() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(99L, Order.OrderStatus.CANCELLED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── deleteOrder ────────────────────────────────────────────────────────────

    @Test
    void deleteOrder_notCancelledStatus_compensatesInventory() {
        Order order = savedOrder(1L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.getItems().add(item(10L, 2));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.deleteOrder(1L);

        verify(inventoryServicePort).restoreStock(10L, 2);
        verify(orderRepository).deleteById(1L);
    }

    @Test
    void deleteOrder_cancelledOrder_skipsCompensation() {
        Order order = savedOrder(1L);
        order.setStatus(Order.OrderStatus.CANCELLED);
        order.getItems().add(item(10L, 2));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.deleteOrder(1L);

        verify(inventoryServicePort, never()).restoreStock(any(), any());
        verify(orderRepository).deleteById(1L);
    }

    @Test
    void deleteOrder_notFound_throwsIllegalArgument() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(orderRepository, never()).deleteById(any());
    }
}
