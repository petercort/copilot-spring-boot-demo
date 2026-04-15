package com.fisglobal.order.client;

import com.fisglobal.order.dto.ProductDto;
import com.fisglobal.order.port.InventoryServicePort;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter that implements {@link InventoryServicePort} using the Feign client.
 * Translates 404 responses into an empty Optional and maps 400 (insufficient stock)
 * responses from reserveStock into a boolean false return, isolating OrderService
 * from Feign-specific exception types.
 */
@Component
public class InventoryServiceClientAdapter implements InventoryServicePort {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceClientAdapter.class);

    private final InventoryServiceClient inventoryServiceClient;

    public InventoryServiceClientAdapter(InventoryServiceClient inventoryServiceClient) {
        this.inventoryServiceClient = inventoryServiceClient;
    }
    @Override
    public Optional<ProductDto> findProductById(Long productId) {
        try {
            return Optional.ofNullable(inventoryServiceClient.getProductById(productId));
        } catch (FeignException.NotFound e) {
            log.debug("Product {} not found in Inventory Service", productId);
            return Optional.empty();
        }
    }

    @Override
    public boolean reserveStock(Long productId, Integer quantity) {
        try {
            inventoryServiceClient.reserveStock(productId, quantity);
            return true;
        } catch (FeignException.BadRequest e) {
            log.warn("Insufficient stock for product {} (quantity: {})", productId, quantity);
            return false;
        }
    }

    @Override
    public void restoreStock(Long productId, Integer quantity) {
        try {
            inventoryServiceClient.restoreStock(productId, quantity);
        } catch (FeignException e) {
            log.error("Failed to restore stock for product {} (quantity: {}): {}",
                    productId, quantity, e.getMessage());
        }
    }
}
