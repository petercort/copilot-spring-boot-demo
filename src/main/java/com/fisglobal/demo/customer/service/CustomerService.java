package com.fisglobal.demo.customer.service;

import com.fisglobal.demo.customer.model.Customer;
import com.fisglobal.demo.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Customer operations.
 * Contains business logic for customer management.
 * In a microservices architecture, this would be part of the Customer Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        log.debug("Fetching all customers");
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(Long id) {
        log.debug("Fetching customer with id: {}", id);
        return customerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerByEmail(String email) {
        log.debug("Fetching customer with email: {}", email);
        return customerRepository.findByEmail(email);
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        log.debug("Creating new customer: {}", customer.getEmail());
        
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException("Customer with email " + customer.getEmail() + " already exists");
        }
        
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer customerDetails) {
        log.debug("Updating customer with id: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + id));

        customer.setFirstName(customerDetails.getFirstName());
        customer.setLastName(customerDetails.getLastName());
        customer.setEmail(customerDetails.getEmail());
        customer.setPhone(customerDetails.getPhone());
        customer.setAddress(customerDetails.getAddress());
        customer.setCity(customerDetails.getCity());
        customer.setState(customerDetails.getState());
        customer.setZipCode(customerDetails.getZipCode());
        customer.setCountry(customerDetails.getCountry());

        return customerRepository.save(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        log.debug("Deleting customer with id: {}", id);
        
        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Customer not found with id: " + id);
        }
        
        customerRepository.deleteById(id);
    }
}
