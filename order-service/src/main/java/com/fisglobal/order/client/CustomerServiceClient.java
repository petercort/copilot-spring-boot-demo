package com.fisglobal.order.client;

import com.fisglobal.order.dto.CustomerDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client declaration for the Customer Service.
 * Registered as a Spring bean via {@link CustomerServiceClientAdapter}, which implements
 * {@link com.fisglobal.order.port.CustomerServicePort} and wraps this interface to provide
 * Optional semantics and translate 404 responses gracefully.
 */
@FeignClient(name = "customer-service", path = "/api/customers", fallback = CustomerServiceClientFallback.class)
public interface CustomerServiceClient {

    @GetMapping("/{id}")
    CustomerDto getCustomerById(@PathVariable Long id);
}
