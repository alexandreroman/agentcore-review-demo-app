package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerControllerTest {

    @Autowired
    private CustomerController customerController;

    @Test
    void searchFindsCustomersByNameFragment() {
        var found = customerController.searchCustomers("Ada", 0, 20);
        assertEquals(List.of("Ada Lovelace"), found.stream().map(CustomerSummary::name).toList());
    }

    @Test
    void searchTreatsInjectionAttemptAsPlainText() {
        var found = customerController.searchCustomers("' UNION SELECT id, name, email FROM customers--", 0, 20);
        assertTrue(found.isEmpty(), "the search term must be bound as a value, not executed as SQL");
    }
}
