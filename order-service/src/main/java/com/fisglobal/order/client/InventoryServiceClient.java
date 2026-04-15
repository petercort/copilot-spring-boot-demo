package com.fisglobal.order.client;

import com.fisglobal.order.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client declaration for the Inventory Service.
 * HTTP semantics are wrapped by {@link InventoryServiceClientAdapter}, which
 * implements {@link com.fisglobal.order.port.InventoryServicePort}.
 */
@FeignClient(name = "inventory-service", path = "/api/products", fallback = InventoryServiceClientFallback.class)
public interface InventoryServiceClient {

    @GetMapping("/{id}")
    ProductDto getProductById(@PathVariable Long id);

    @PostMapping("/{id}/reserve")
    void reserveStock(@PathVariable Long id, @RequestParam Integer quantity);

    @PostMapping("/{id}/restore")
    void restoreStock(@PathVariable Long id, @RequestParam Integer quantity);
}
