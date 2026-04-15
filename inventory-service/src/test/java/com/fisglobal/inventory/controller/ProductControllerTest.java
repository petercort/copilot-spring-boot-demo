package com.fisglobal.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fisglobal.inventory.model.Product;
import com.fisglobal.inventory.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ProductService productService;

    @Autowired
    ObjectMapper objectMapper;

    private Product sample(Long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Widget");
        p.setSku("SKU-001");
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStockQuantity(10);
        p.setCategory("Electronics");
        p.setActive(true);
        return p;
    }

    @Test
    void getAllProducts_noParam_returnsAll() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(sample(1L)));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"));
    }

    @Test
    void getAllProducts_activeOnlyTrue_returnsActive() throws Exception {
        when(productService.getActiveProducts()).thenReturn(List.of(sample(1L)));

        mvc.perform(get("/api/products").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"));
    }

    @Test
    void getProductById_found_returns200() throws Exception {
        when(productService.getProductById(1L)).thenReturn(Optional.of(sample(1L)));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getProductById_notFound_returns404() throws Exception {
        when(productService.getProductById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductBySku_found_returns200() throws Exception {
        when(productService.getProductBySku("SKU-001")).thenReturn(Optional.of(sample(1L)));

        mvc.perform(get("/api/products/sku/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-001"));
    }

    @Test
    void getProductBySku_notFound_returns404() throws Exception {
        when(productService.getProductBySku("MISSING")).thenReturn(Optional.empty());

        mvc.perform(get("/api/products/sku/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductsByCategory_returns200() throws Exception {
        when(productService.getProductsByCategory("Electronics")).thenReturn(List.of(sample(1L)));

        mvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Electronics"));
    }

    @Test
    void getLowStockProducts_defaultThreshold_returns200() throws Exception {
        when(productService.getLowStockProducts(10)).thenReturn(List.of(sample(1L)));

        mvc.perform(get("/api/products/low-stock"))
                .andExpect(status().isOk());
    }

    @Test
    void createProduct_valid_returns201() throws Exception {
        Product input = sample(null);
        Product saved = sample(1L);
        when(productService.createProduct(any(Product.class))).thenReturn(saved);

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateProduct_found_returns200() throws Exception {
        Product updated = sample(1L);
        updated.setName("Updated Widget");
        when(productService.updateProduct(eq(1L), any(Product.class))).thenReturn(updated);

        mvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sample(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Widget"));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        when(productService.updateProduct(eq(99L), any(Product.class)))
                .thenThrow(new IllegalArgumentException("not found"));

        mvc.perform(put("/api/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sample(null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserveStock_success_returns200() throws Exception {
        when(productService.reserveStock(1L, 3)).thenReturn(true);

        mvc.perform(post("/api/products/1/reserve").param("quantity", "3"))
                .andExpect(status().isOk());
    }

    @Test
    void reserveStock_insufficientStock_returns400() throws Exception {
        when(productService.reserveStock(1L, 100)).thenReturn(false);

        mvc.perform(post("/api/products/1/reserve").param("quantity", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reserveStock_productNotFound_returns404() throws Exception {
        when(productService.reserveStock(99L, 1))
                .thenThrow(new IllegalArgumentException("not found"));

        mvc.perform(post("/api/products/99/reserve").param("quantity", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreStock_success_returns200() throws Exception {
        doNothing().when(productService).restoreStock(1L, 2);

        mvc.perform(post("/api/products/1/restore").param("quantity", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void restoreStock_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(productService).restoreStock(99L, 1);

        mvc.perform(post("/api/products/99/restore").param("quantity", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_found_returns204() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProduct_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(productService).deleteProduct(99L);

        mvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound());
    }
}
