package com.fisglobal.order.client;

import com.fisglobal.order.dto.CustomerDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceClientAdapterTest {

    @Mock
    CustomerServiceClient customerServiceClient;

    @InjectMocks
    CustomerServiceClientAdapter adapter;

    private CustomerDto customerDto(long id) {
        return new CustomerDto(id, "Jane", "Doe", "jane@example.com", "555-0000",
                "1 Main St", "Springfield", "IL", "62701", "US", null, null);
    }

    @Test
    void findById_success_returnsOptional() {
        CustomerDto dto = customerDto(1L);
        when(customerServiceClient.getCustomerById(1L)).thenReturn(dto);

        Optional<CustomerDto> result = adapter.findById(1L);

        assertThat(result).contains(dto);
    }

    @Test
    void findById_feignNotFound_returnsEmptyOptional() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(customerServiceClient.getCustomerById(99L)).thenThrow(notFound);

        Optional<CustomerDto> result = adapter.findById(99L);

        assertThat(result).isEmpty();
    }
}
