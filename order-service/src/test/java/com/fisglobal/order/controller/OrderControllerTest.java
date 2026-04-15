package com.fisglobal.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fisglobal.order.dto.CreateOrderRequest;
import com.fisglobal.order.dto.OrderItemRequest;
import com.fisglobal.order.model.Order;
import com.fisglobal.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    OrderService orderService;

    @Autowired
    ObjectMapper objectMapper;

    private Order sampleOrder(long id) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNumber("ORD-" + id);
        o.setCustomerId(1L);
        o.setStatus(Order.OrderStatus.CONFIRMED);
        o.setTotalAmount(BigDecimal.valueOf(20.00));
        return o;
    }

    @Test
    void getAllOrders_returnsOk() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder(1L)));

        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-1"));
    }

    @Test
    void getOrderById_found_returns200() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(sampleOrder(1L)));

        mvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        when(orderService.getOrderById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderByOrderNumber_found_returns200() throws Exception {
        when(orderService.getOrderByOrderNumber("ORD-1")).thenReturn(Optional.of(sampleOrder(1L)));

        mvc.perform(get("/api/orders/order-number/ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-1"));
    }

    @Test
    void getOrderByOrderNumber_notFound_returns404() throws Exception {
        when(orderService.getOrderByOrderNumber("MISSING")).thenReturn(Optional.empty());

        mvc.perform(get("/api/orders/order-number/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrdersByCustomerId_returns200() throws Exception {
        when(orderService.getOrdersByCustomerId(1L)).thenReturn(List.of(sampleOrder(1L)));

        mvc.perform(get("/api/orders/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(1));
    }

    @Test
    void getOrdersByStatus_returns200() throws Exception {
        when(orderService.getOrdersByStatus(Order.OrderStatus.CONFIRMED))
                .thenReturn(List.of(sampleOrder(1L)));

        mvc.perform(get("/api/orders/status/CONFIRMED"))
                .andExpect(status().isOk());
    }

    @Test
    void createOrder_valid_returns201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, List.of(new OrderItemRequest(10L, 2)),
                "1 Main St", "Springfield", "IL", "62701", "US");
        Order created = sampleOrder(1L);
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(created);

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createOrder_illegalArgument_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                99L, List.of(new OrderItemRequest(10L, 1)), null, null, null, null, null);
        when(orderService.createOrder(any(CreateOrderRequest.class)))
                .thenThrow(new IllegalArgumentException("Customer not found"));

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateOrderStatus_found_returns200() throws Exception {
        Order updated = sampleOrder(1L);
        updated.setStatus(Order.OrderStatus.PROCESSING);
        when(orderService.updateOrderStatus(1L, Order.OrderStatus.PROCESSING)).thenReturn(updated);

        mvc.perform(patch("/api/orders/1/status").param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void updateOrderStatus_notFound_returns404() throws Exception {
        when(orderService.updateOrderStatus(eq(99L), any(Order.OrderStatus.class)))
                .thenThrow(new IllegalArgumentException("not found"));

        mvc.perform(patch("/api/orders/99/status").param("status", "CANCELLED"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOrder_found_returns204() throws Exception {
        doNothing().when(orderService).deleteOrder(1L);

        mvc.perform(delete("/api/orders/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOrder_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(orderService).deleteOrder(99L);

        mvc.perform(delete("/api/orders/99"))
                .andExpect(status().isNotFound());
    }
}
