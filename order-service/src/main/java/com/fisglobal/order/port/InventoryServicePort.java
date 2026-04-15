package com.fisglobal.order.port;

import com.fisglobal.order.dto.ProductDto;

import java.util.Optional;

/**
 * Port interface for inventory-related operations required by the Order Service.
 * Decouples OrderService from the transport mechanism (HTTP/Feign).
 * The Feign client adapter implements this interface in production;
 * a mock implementation can be used in unit tests.
 */
public interface InventoryServicePort {

    Optional<ProductDto> findProductById(Long productId);

    boolean reserveStock(Long productId, Integer quantity);

    void restoreStock(Long productId, Integer quantity);
}
