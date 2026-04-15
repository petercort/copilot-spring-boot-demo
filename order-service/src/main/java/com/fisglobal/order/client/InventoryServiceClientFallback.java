package com.fisglobal.order.client;

import com.fisglobal.order.dto.ProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryServiceClientFallback implements InventoryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceClientFallback.class);

    @Override
    public ProductDto getProductById(Long id) {
        log.warn("InventoryServiceClient fallback triggered for productId={}", id);
        return null;
    }

    @Override
    public void reserveStock(Long id, Integer quantity) {
        log.warn("InventoryServiceClient.reserveStock fallback for productId={}, qty={}", id, quantity);
        throw new RuntimeException("Inventory service unavailable — cannot reserve stock");
    }

    @Override
    public void restoreStock(Long id, Integer quantity) {
        log.warn("InventoryServiceClient.restoreStock fallback for productId={}, qty={}", id, quantity);
    }
}
