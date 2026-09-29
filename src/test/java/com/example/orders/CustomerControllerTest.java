package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Covers the search and order history endpoints against the {@link SampleData} fixtures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomerControllerTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void searchMatchesNameAndEmailIgnoringCase() {
        assertEquals(List.of("Ada Lovelace"), names(customerController.searchCustomers("LOVELACE", 0, 20)));
        assertEquals(List.of("Grace Hopper"), names(customerController.searchCustomers("grace@example.com", 0, 20)));
    }

    @Test
    void searchTreatsLikeWildcardsAsLiterals() {
        assertTrue(customerController.searchCustomers("%", 0, 20).isEmpty());
        assertTrue(customerController.searchCustomers("_", 0, 20).isEmpty());
    }

    @Test
    void searchIsPagedByNameAndEmptyBeyondTheLastPage() {
        assertEquals(List.of("Ada Lovelace"), names(customerController.searchCustomers("example.com", 0, 1)));
        assertEquals(List.of("Alan Turing"), names(customerController.searchCustomers("example.com", 1, 1)));
        assertTrue(customerController.searchCustomers("example.com", 5, 20).isEmpty());
    }

    @Test
    void searchRejectsBlankOrOverlongTerms() {
        assertBadRequest(() -> customerController.searchCustomers("   ", 0, 20));
        assertBadRequest(() -> customerController.searchCustomers("a".repeat(101), 0, 20));
    }

    @Test
    void bothEndpointsRejectInvalidPaging() {
        assertBadRequest(() -> customerController.searchCustomers("ada", 0, 0));
        assertBadRequest(() -> customerController.searchCustomers("ada", 0, 101));
        assertBadRequest(() -> customerController.searchCustomers("ada", -1, 20));
        assertBadRequest(() -> customerController.customerOrders(customerId("ada@example.com"), 0, 0));
        assertBadRequest(() -> customerController.customerOrders(customerId("ada@example.com"), 0, 101));
        assertBadRequest(() -> customerController.customerOrders(customerId("ada@example.com"), -1, 20));
    }

    @Test
    void orderHistoryIsNewestFirstWithLineCountAndTotal() {
        var history = customerController.customerOrders(customerId("grace@example.com"), 0, 20);

        assertEquals(2, history.size());
        assertTrue(history.get(0).id() > history.get(1).id());
        for (var summary : history) {
            // Two orders of 2 notebooks at 450 and 20 pens at 120, the bulk line getting 5% off.
            assertEquals(2, summary.lineCount());
            assertEquals(2 * 450 + 20 * 120 * 95 / 100, summary.totalCents());
        }
    }

    @Test
    void orderHistoryIsPagedAndEmptyBeyondTheLastPage() {
        var alan = customerId("alan@example.com");

        assertEquals(1, customerController.customerOrders(alan, 0, 1).size());
        assertEquals(3, customerController.customerOrders(alan, 0, 20).size());
        assertTrue(customerController.customerOrders(alan, 5, 20).isEmpty());
    }

    private static List<String> names(List<CustomerSummary> customers) {
        return customers.stream()
                .map(CustomerSummary::name)
                .toList();
    }

    private long customerId(String email) {
        return customerRepository.findAll().stream()
                .filter(customer -> email.equals(customer.getEmail()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static void assertBadRequest(Executable call) {
        var exception = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
