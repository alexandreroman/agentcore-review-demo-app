package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void searchRejectsTermThatWouldMatchEveryCustomer() {
        var failure = assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers(" ", 0, 20));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    void searchRejectsInvalidPageSize() {
        var failure = assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("Ada", 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    void customerOrdersSummarisesLinesOfEveryOrder() {
        var grace = customerController.searchCustomers("Grace", 0, 20).get(0);
        var history = customerController.customerOrders(grace.id(), 0, 20);
        assertEquals(2, history.size());
        for (var summary : history) {
            assertEquals(2, summary.lineCount());
            assertEquals(2 * 450 + 20 * 120 * 95 / 100, summary.totalCents());
        }
    }
}
