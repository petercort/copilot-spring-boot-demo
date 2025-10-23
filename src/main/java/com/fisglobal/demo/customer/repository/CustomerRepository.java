package com.fisglobal.demo.customer.repository;

import com.fisglobal.demo.customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Customer entity.
 * In a microservices architecture, this would be isolated in the Customer Service.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    Optional<Customer> findByEmail(String email);
    
    boolean existsByEmail(String email);
}
