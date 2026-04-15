package com.fisglobal.order.client;

import com.fisglobal.order.dto.CustomerDto;
import com.fisglobal.order.port.CustomerServicePort;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter that implements {@link CustomerServicePort} using the Feign client.
 * Translates 404 HTTP responses into an empty Optional so that callers
 * (OrderService) interact only with the port interface, not with Feign internals.
 */
@Component
public class CustomerServiceClientAdapter implements CustomerServicePort {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClientAdapter.class);

    private final CustomerServiceClient customerServiceClient;

    public CustomerServiceClientAdapter(CustomerServiceClient customerServiceClient) {
        this.customerServiceClient = customerServiceClient;
    }
    @Override
    public Optional<CustomerDto> findById(Long customerId) {
        try {
            return Optional.ofNullable(customerServiceClient.getCustomerById(customerId));
        } catch (FeignException.NotFound e) {
            log.debug("Customer {} not found in Customer Service", customerId);
            return Optional.empty();
        }
    }
}
