package com.fisglobal.order.client;

import com.fisglobal.order.dto.CustomerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomerServiceClientFallback implements CustomerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClientFallback.class);

    @Override
    public CustomerDto getCustomerById(Long id) {
        log.warn("CustomerServiceClient fallback triggered for customerId={}", id);
        return null;
    }
}
