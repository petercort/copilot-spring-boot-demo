package com.fisglobal.order.client;

import com.fisglobal.order.dto.ProductDto;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceClientAdapterTest {

    @Mock
    InventoryServiceClient inventoryServiceClient;

    @InjectMocks
    InventoryServiceClientAdapter adapter;

    private ProductDto productDto(long id) {
        return new ProductDto(id, "Widget", "Desc", "SKU-" + id,
                BigDecimal.valueOf(9.99), 10, "Electronics", 5, true, null, null);
    }

    @Test
    void findProductById_success_returnsOptional() {
        ProductDto dto = productDto(1L);
        when(inventoryServiceClient.getProductById(1L)).thenReturn(dto);

        Optional<ProductDto> result = adapter.findProductById(1L);

        assertThat(result).contains(dto);
    }

    @Test
    void findProductById_feignNotFound_returnsEmpty() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(inventoryServiceClient.getProductById(99L)).thenThrow(notFound);

        Optional<ProductDto> result = adapter.findProductById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void reserveStock_success_returnsTrue() {
        doNothing().when(inventoryServiceClient).reserveStock(1L, 3);

        boolean result = adapter.reserveStock(1L, 3);

        assertThat(result).isTrue();
    }

    @Test
    void reserveStock_feignBadRequest_returnsFalse() {
        FeignException.BadRequest badRequest = mock(FeignException.BadRequest.class);
        doThrow(badRequest).when(inventoryServiceClient).reserveStock(1L, 100);

        boolean result = adapter.reserveStock(1L, 100);

        assertThat(result).isFalse();
    }

    @Test
    void restoreStock_success() {
        doNothing().when(inventoryServiceClient).restoreStock(1L, 2);

        adapter.restoreStock(1L, 2);

        verify(inventoryServiceClient).restoreStock(1L, 2);
    }

    @Test
    void restoreStock_feignException_doesNotThrow() {
        FeignException feignEx = mock(FeignException.class);
        doThrow(feignEx).when(inventoryServiceClient).restoreStock(1L, 3);

        // Should swallow the exception and not rethrow
        adapter.restoreStock(1L, 3);
    }
}
