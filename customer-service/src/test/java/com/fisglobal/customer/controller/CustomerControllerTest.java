package com.fisglobal.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fisglobal.customer.model.Customer;
import com.fisglobal.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CustomerService customerService;

    @Autowired
    ObjectMapper objectMapper;

    private Customer sample(Long id) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setEmail("jane@example.com");
        c.setPhone("555-0000");
        return c;
    }

    @Test
    void getAllCustomers_returnsOk() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of(sample(1L)));

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("jane@example.com"));
    }

    @Test
    void getCustomerById_found_returns200() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(sample(1L)));

        mvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getCustomerById_notFound_returns404() throws Exception {
        when(customerService.getCustomerById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCustomerByEmail_found_returns200() throws Exception {
        when(customerService.getCustomerByEmail("jane@example.com"))
                .thenReturn(Optional.of(sample(1L)));

        mvc.perform(get("/api/customers/email/jane@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void getCustomerByEmail_notFound_returns404() throws Exception {
        when(customerService.getCustomerByEmail("x@y.com")).thenReturn(Optional.empty());

        mvc.perform(get("/api/customers/email/x@y.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCustomer_valid_returns201() throws Exception {
        Customer input = sample(null);
        Customer saved = sample(1L);
        when(customerService.createCustomer(any(Customer.class))).thenReturn(saved);

        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateCustomer_found_returns200() throws Exception {
        Customer updated = sample(1L);
        updated.setFirstName("Updated");
        when(customerService.updateCustomer(eq(1L), any(Customer.class))).thenReturn(updated);

        mvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sample(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    @Test
    void updateCustomer_notFound_returns404() throws Exception {
        when(customerService.updateCustomer(eq(99L), any(Customer.class)))
                .thenThrow(new IllegalArgumentException("not found"));

        mvc.perform(put("/api/customers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sample(null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomer_found_returns204() throws Exception {
        doNothing().when(customerService).deleteCustomer(1L);

        mvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCustomer_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(customerService).deleteCustomer(99L);

        mvc.perform(delete("/api/customers/99"))
                .andExpect(status().isNotFound());
    }
}
