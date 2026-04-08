package com.fisglobal.inventory.service;

import com.fisglobal.inventory.model.Product;
import com.fisglobal.inventory.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductService productService;

    private Product buildProduct(Long id, String sku, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Widget");
        p.setSku(sku);
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStockQuantity(stock);
        p.setCategory("Electronics");
        p.setReorderLevel(5);
        p.setActive(true);
        return p;
    }

    @Test
    void getAllProducts_returnsList() {
        when(productRepository.findAll()).thenReturn(List.of(buildProduct(1L, "SKU-1", 10)));

        List<Product> result = productService.getAllProducts();

        assertThat(result).hasSize(1);
        verify(productRepository).findAll();
    }

    @Test
    void getActiveProducts_returnsOnlyActive() {
        when(productRepository.findByActiveTrue()).thenReturn(List.of(buildProduct(1L, "SKU-1", 10)));

        List<Product> result = productService.getActiveProducts();

        assertThat(result).hasSize(1);
        verify(productRepository).findByActiveTrue();
    }

    @Test
    void getProductById_found() {
        Product p = buildProduct(1L, "SKU-1", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        Optional<Product> result = productService.getProductById(1L);

        assertThat(result).contains(p);
    }

    @Test
    void getProductById_notFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Product> result = productService.getProductById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getProductBySku_found() {
        Product p = buildProduct(1L, "SKU-1", 10);
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(p));

        Optional<Product> result = productService.getProductBySku("SKU-1");

        assertThat(result).contains(p);
    }

    @Test
    void getProductBySku_notFound() {
        when(productRepository.findBySku("MISSING")).thenReturn(Optional.empty());

        Optional<Product> result = productService.getProductBySku("MISSING");

        assertThat(result).isEmpty();
    }

    @Test
    void getProductsByCategory_returnsList() {
        when(productRepository.findByCategory("Electronics"))
                .thenReturn(List.of(buildProduct(1L, "SKU-1", 10)));

        List<Product> result = productService.getProductsByCategory("Electronics");

        assertThat(result).hasSize(1);
    }

    @Test
    void getLowStockProducts_returnsList() {
        when(productRepository.findByStockQuantityLessThanEqual(5))
                .thenReturn(List.of(buildProduct(1L, "SKU-1", 3)));

        List<Product> result = productService.getLowStockProducts(5);

        assertThat(result).hasSize(1);
    }

    @Test
    void createProduct_success() {
        Product input = buildProduct(null, "NEW-1", 20);
        Product saved = buildProduct(1L, "NEW-1", 20);
        when(productRepository.existsBySku("NEW-1")).thenReturn(false);
        when(productRepository.save(input)).thenReturn(saved);

        Product result = productService.createProduct(input);

        assertThat(result.getId()).isEqualTo(1L);
        verify(productRepository).save(input);
    }

    @Test
    void createProduct_duplicateSku_throwsIllegalArgument() {
        Product input = buildProduct(null, "DUP-1", 20);
        when(productRepository.existsBySku("DUP-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUP-1");

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_success() {
        Product existing = buildProduct(1L, "SKU-1", 10);
        Product details = buildProduct(null, "SKU-1-UPDATED", 20);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Product result = productService.updateProduct(1L, details);

        assertThat(result.getSku()).isEqualTo("SKU-1-UPDATED");
        assertThat(result.getStockQuantity()).isEqualTo(20);
    }

    @Test
    void updateProduct_notFound_throwsIllegalArgument() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, buildProduct(null, "S", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void reserveStock_success_returnsTrue() {
        Product p = buildProduct(1L, "SKU-1", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(p)).thenReturn(p);

        boolean result = productService.reserveStock(1L, 3);

        assertThat(result).isTrue();
        assertThat(p.getStockQuantity()).isEqualTo(7);
        verify(productRepository).save(p);
    }

    @Test
    void reserveStock_insufficientStock_returnsFalse() {
        Product p = buildProduct(1L, "SKU-1", 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        boolean result = productService.reserveStock(1L, 5);

        assertThat(result).isFalse();
        verify(productRepository, never()).save(any());
    }

    @Test
    void reserveStock_productNotFound_throwsIllegalArgument() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.reserveStock(99L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void restoreStock_success() {
        Product p = buildProduct(1L, "SKU-1", 5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(p)).thenReturn(p);

        productService.restoreStock(1L, 3);

        assertThat(p.getStockQuantity()).isEqualTo(8);
        verify(productRepository).save(p);
    }

    @Test
    void restoreStock_notFound_throwsIllegalArgument() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.restoreStock(99L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteProduct_success() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_notFound_throwsIllegalArgument() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).deleteById(any());
    }
}
