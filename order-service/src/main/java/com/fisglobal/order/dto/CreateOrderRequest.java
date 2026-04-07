package com.fisglobal.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required") Long customerId,
    @NotEmpty(message = "Order must have at least one item") @Valid List<OrderItemRequest> items,
    String shippingAddress,
    String shippingCity,
    String shippingState,
    String shippingZip,
    String shippingCountry
) {}
