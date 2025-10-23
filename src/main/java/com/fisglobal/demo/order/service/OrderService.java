package com.fisglobal.demo.order.service;

import com.fisglobal.demo.customer.model.Customer;
import com.fisglobal.demo.customer.service.CustomerService;
import com.fisglobal.demo.inventory.model.Product;
import com.fisglobal.demo.inventory.service.ProductService;
import com.fisglobal.demo.order.dto.CreateOrderRequest;
import com.fisglobal.demo.order.dto.OrderItemRequest;
import com.fisglobal.demo.order.model.Order;
import com.fisglobal.demo.order.model.OrderItem;
import com.fisglobal.demo.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Order operations.
 * Contains business logic for order management.
 * 
 * NOTE: This service demonstrates tight coupling between domains (Order, Customer, Inventory).
 * In a microservices architecture:
 * - This would be part of the Order Service
 * - It would communicate with Customer and Inventory services via REST/messaging
 * - Cross-domain transactions would need to be handled differently (e.g., Saga pattern)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        log.debug("Fetching all orders");
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        log.debug("Fetching order with id: {}", id);
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderByOrderNumber(String orderNumber) {
        log.debug("Fetching order with order number: {}", orderNumber);
        return orderRepository.findByOrderNumber(orderNumber);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomerId(Long customerId) {
        log.debug("Fetching orders for customer: {}", customerId);
        return orderRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        log.debug("Fetching orders with status: {}", status);
        return orderRepository.findByStatus(status);
    }

    /**
     * Create a new order.
     * This method demonstrates cross-domain operations that would need to be
     * redesigned in a microservices architecture.
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.debug("Creating new order for customer: {}", request.getCustomerId());

        // Validate customer exists (cross-domain dependency)
        Customer customer = customerService.getCustomerById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found with id: " + request.getCustomerId()));

        // Create order
        Order order = new Order();
        order.setCustomerId(customer.getId());
        order.setShippingAddress(request.getShippingAddress());
        order.setShippingCity(request.getShippingCity());
        order.setShippingState(request.getShippingState());
        order.setShippingZip(request.getShippingZip());
        order.setShippingCountry(request.getShippingCountry());

        // Process each order item (cross-domain dependency)
        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productService.getProductById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product not found with id: " + itemRequest.getProductId()));

            if (!product.isInStock()) {
                throw new IllegalArgumentException(
                        "Product " + product.getName() + " is out of stock");
            }

            // Reserve inventory (cross-domain operation)
            boolean reserved = productService.reserveStock(
                    product.getId(), itemRequest.getQuantity());
            
            if (!reserved) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: " + product.getName());
            }

            // Create order item
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setProductSku(product.getSku());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(product.getPrice());
            
            order.addItem(orderItem);
        }

        order.setStatus(Order.OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);
        
        log.info("Created order {} for customer {}", savedOrder.getOrderNumber(), customer.getId());
        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        log.debug("Updating order {} status to {}", id, newStatus);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + id));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        // If order is cancelled, restore inventory (cross-domain operation)
        if (newStatus == Order.OrderStatus.CANCELLED && oldStatus != Order.OrderStatus.CANCELLED) {
            log.info("Cancelling order {}, restoring inventory", order.getOrderNumber());
            for (OrderItem item : order.getItems()) {
                productService.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Updated order {} status from {} to {}", 
                savedOrder.getOrderNumber(), oldStatus, newStatus);
        
        return savedOrder;
    }

    @Transactional
    public void deleteOrder(Long id) {
        log.debug("Deleting order with id: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + id));

        // Restore inventory if order was not cancelled (cross-domain operation)
        if (order.getStatus() != Order.OrderStatus.CANCELLED) {
            log.info("Restoring inventory for deleted order {}", order.getOrderNumber());
            for (OrderItem item : order.getItems()) {
                productService.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        orderRepository.deleteById(id);
        log.info("Deleted order {}", order.getOrderNumber());
    }
}
