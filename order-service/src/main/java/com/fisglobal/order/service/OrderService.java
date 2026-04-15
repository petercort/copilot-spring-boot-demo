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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Order operations.
 *
 * Cross-domain calls go through {@link CustomerServicePort} and {@link InventoryServicePort},
 * which are implemented by Feign client adapters in production and can be replaced by
 * mock implementations in unit tests without starting any HTTP infrastructure.
 *
 * createOrder uses an Orchestration Saga pattern:
 *   1. Validate customer exists (abort if not).
 *   2. For each item: reserve stock. If any reservation fails, compensate by
 *      restoring all previously reserved items, then abort with an exception.
 *   3. Persist the order (scoped @Transactional only to the Order DB).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CustomerServicePort customerServicePort;
    private final InventoryServicePort inventoryServicePort;

    public OrderService(OrderRepository orderRepository,
                        CustomerServicePort customerServicePort,
                        InventoryServicePort inventoryServicePort) {
        this.orderRepository = orderRepository;
        this.customerServicePort = customerServicePort;
        this.inventoryServicePort = inventoryServicePort;
    }
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
     * Create a new order using an Orchestration Saga.
     *
     * Steps:
     *   1. Validate customer via Customer Service.
     *   2. For each item: fetch product, then reserve stock.
     *      On failure at any step, compensate (restore) all previously reserved items
     *      before re-throwing.
     *   3. Persist the assembled order — this @Transactional boundary covers only
     *      the Order Service's own database.
     */
    public Order createOrder(CreateOrderRequest request) {
        log.debug("Creating new order for customer: {}", request.customerId());

        CustomerDto customer = customerServicePort.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found with id: " + request.customerId()));

        Order order = new Order();
        order.setCustomerId(customer.id());
        order.setShippingAddress(request.shippingAddress());
        order.setShippingCity(request.shippingCity());
        order.setShippingState(request.shippingState());
        order.setShippingZip(request.shippingZip());
        order.setShippingCountry(request.shippingCountry());

        // Track successfully reserved items so we can compensate on failure
        List<OrderItemRequest> reservedItems = new ArrayList<>();

        try {
            for (OrderItemRequest itemRequest : request.items()) {
                ProductDto product = inventoryServicePort.findProductById(itemRequest.productId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Product not found with id: " + itemRequest.productId()));

                if (!product.isInStock()) {
                    throw new IllegalArgumentException(
                            "Product " + product.name() + " is out of stock");
                }

                boolean reserved = inventoryServicePort.reserveStock(
                        product.id(), itemRequest.quantity());

                if (!reserved) {
                    throw new IllegalArgumentException(
                            "Insufficient stock for product: " + product.name());
                }

                reservedItems.add(itemRequest);

                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(product.id());
                orderItem.setProductName(product.name());
                orderItem.setProductSku(product.sku());
                orderItem.setQuantity(itemRequest.quantity());
                orderItem.setUnitPrice(product.price());

                order.addItem(orderItem);
            }
        } catch (IllegalArgumentException e) {
            // Compensate: restore all inventory already reserved in this saga
            log.warn("Order creation failed for customer {}. Compensating {} reserved items. Reason: {}",
                    customer.id(), reservedItems.size(), e.getMessage());
            for (OrderItemRequest reserved : reservedItems) {
                inventoryServicePort.restoreStock(reserved.productId(), reserved.quantity());
            }
            throw e;
        }

        order.setStatus(Order.OrderStatus.CONFIRMED);
        Order savedOrder = persistOrder(order);

        log.info("Created order {} for customer {}", savedOrder.getOrderNumber(), customer.id());
        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        log.debug("Updating order {} status to {}", id, newStatus);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + id));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        if (newStatus == Order.OrderStatus.CANCELLED && oldStatus != Order.OrderStatus.CANCELLED) {
            log.info("Cancelling order {}, restoring inventory", order.getOrderNumber());
            for (OrderItem item : order.getItems()) {
                inventoryServicePort.restoreStock(item.getProductId(), item.getQuantity());
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

        if (order.getStatus() != Order.OrderStatus.CANCELLED) {
            log.info("Restoring inventory for deleted order {}", order.getOrderNumber());
            for (OrderItem item : order.getItems()) {
                inventoryServicePort.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        orderRepository.deleteById(id);
        log.info("Deleted order {}", order.getOrderNumber());
    }

    /**
     * Persists the fully assembled order within a local @Transactional boundary.
     * Kept separate so the transaction scope covers only the Order DB write,
     * not the cross-service HTTP calls above.
     */
    @Transactional
    protected Order persistOrder(Order order) {
        return orderRepository.save(order);
    }
}
