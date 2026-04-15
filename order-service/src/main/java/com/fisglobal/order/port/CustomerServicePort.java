package com.fisglobal.order.port;

import com.fisglobal.order.dto.CustomerDto;

import java.util.Optional;

/**
 * Port interface for customer-related operations required by the Order Service.
 * Decouples OrderService from the transport mechanism (HTTP/Feign).
 * The Feign client adapter implements this interface in production;
 * a mock implementation can be used in unit tests.
 */
public interface CustomerServicePort {

    Optional<CustomerDto> findById(Long customerId);
}
