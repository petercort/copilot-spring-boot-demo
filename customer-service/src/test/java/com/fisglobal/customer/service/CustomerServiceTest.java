package com.fisglobal.customer.service;

import com.fisglobal.customer.model.Customer;
import com.fisglobal.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    CustomerRepository customerRepository;

    @InjectMocks
    CustomerService customerService;

    private Customer buildCustomer(Long id, String email) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setEmail(email);
        c.setPhone("555-0000");
        return c;
    }

    @Test
    void getAllCustomers_returnsList() {
        when(customerRepository.findAll()).thenReturn(List.of(buildCustomer(1L, "a@b.com")));

        List<Customer> result = customerService.getAllCustomers();

        assertThat(result).hasSize(1);
        verify(customerRepository).findAll();
    }

    @Test
    void getCustomerById_found() {
        Customer customer = buildCustomer(1L, "a@b.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Optional<Customer> result = customerService.getCustomerById(1L);

        assertThat(result).contains(customer);
    }

    @Test
    void getCustomerById_notFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Customer> result = customerService.getCustomerById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getCustomerByEmail_found() {
        Customer customer = buildCustomer(1L, "a@b.com");
        when(customerRepository.findByEmail("a@b.com")).thenReturn(Optional.of(customer));

        Optional<Customer> result = customerService.getCustomerByEmail("a@b.com");

        assertThat(result).contains(customer);
    }

    @Test
    void getCustomerByEmail_notFound() {
        when(customerRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

        Optional<Customer> result = customerService.getCustomerByEmail("x@y.com");

        assertThat(result).isEmpty();
    }

    @Test
    void createCustomer_success() {
        Customer input = buildCustomer(null, "new@b.com");
        Customer saved = buildCustomer(2L, "new@b.com");
        when(customerRepository.existsByEmail("new@b.com")).thenReturn(false);
        when(customerRepository.save(input)).thenReturn(saved);

        Customer result = customerService.createCustomer(input);

        assertThat(result.getId()).isEqualTo(2L);
        verify(customerRepository).save(input);
    }

    @Test
    void createCustomer_duplicateEmail_throwsIllegalArgument() {
        Customer input = buildCustomer(null, "dup@b.com");
        when(customerRepository.existsByEmail("dup@b.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup@b.com");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_success() {
        Customer existing = buildCustomer(1L, "old@b.com");
        Customer details = buildCustomer(null, "new@b.com");
        details.setFirstName("Updated");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(existing)).thenReturn(existing);

        Customer result = customerService.updateCustomer(1L, details);

        assertThat(result.getFirstName()).isEqualTo("Updated");
        assertThat(result.getEmail()).isEqualTo("new@b.com");
        verify(customerRepository).save(existing);
    }

    @Test
    void updateCustomer_notFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(99L, buildCustomer(null, "x@y.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteCustomer_success() {
        when(customerRepository.existsById(1L)).thenReturn(true);

        customerService.deleteCustomer(1L);

        verify(customerRepository).deleteById(1L);
    }

    @Test
    void deleteCustomer_notFound() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.deleteCustomer(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(customerRepository, never()).deleteById(any());
    }
}
