package com.fisglobal.customer.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    private Customer buildCustomer(Long id) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName("Jane");
        c.setLastName("Doe");
        c.setEmail("jane@example.com");
        c.setPhone("555-0000");
        return c;
    }

    @Test
    void onCreate_setsTimestamps() {
        Customer c = buildCustomer(null);
        c.onCreate();

        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getUpdatedAt()).isNotNull();
    }

    @Test
    void onUpdate_updatesTimestamp() throws InterruptedException {
        Customer c = buildCustomer(null);
        c.onCreate();
        var after = c.getUpdatedAt();

        // Ensure a measurable time difference
        Thread.sleep(1);
        c.onUpdate();

        assertThat(c.getUpdatedAt()).isAfterOrEqualTo(after);
    }

    @Test
    void equals_sameId_returnsTrue() {
        Customer a = buildCustomer(1L);
        Customer b = buildCustomer(1L);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentId_returnsFalse() {
        Customer a = buildCustomer(1L);
        Customer b = buildCustomer(2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_nullId_returnsFalse() {
        Customer a = buildCustomer(null);
        Customer b = buildCustomer(null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Customer a = buildCustomer(1L);
        Customer b = buildCustomer(1L);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
