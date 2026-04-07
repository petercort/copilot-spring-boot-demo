package com.fisglobal.customer.config;

import com.fisglobal.customer.model.Customer;
import com.fisglobal.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initCustomers(CustomerRepository customerRepository) {
        return args -> {
            if (customerRepository.count() > 0) {
                return;
            }
            log.info("Seeding customer data...");

            Customer c1 = new Customer();
            c1.setFirstName("John");
            c1.setLastName("Doe");
            c1.setEmail("john.doe@example.com");
            c1.setPhone("555-1234");
            c1.setAddress("123 Main St");
            c1.setCity("New York");
            c1.setState("NY");
            c1.setZipCode("10001");
            c1.setCountry("USA");
            customerRepository.save(c1);

            Customer c2 = new Customer();
            c2.setFirstName("Jane");
            c2.setLastName("Smith");
            c2.setEmail("jane.smith@example.com");
            c2.setPhone("555-5678");
            c2.setAddress("456 Oak Ave");
            c2.setCity("Los Angeles");
            c2.setState("CA");
            c2.setZipCode("90001");
            c2.setCountry("USA");
            customerRepository.save(c2);

            Customer c3 = new Customer();
            c3.setFirstName("Bob");
            c3.setLastName("Johnson");
            c3.setEmail("bob.johnson@example.com");
            c3.setPhone("555-9012");
            c3.setAddress("789 Pine Rd");
            c3.setCity("Chicago");
            c3.setState("IL");
            c3.setZipCode("60601");
            c3.setCountry("USA");
            customerRepository.save(c3);

            log.info("Seeded {} customers", customerRepository.count());
        };
    }
}
