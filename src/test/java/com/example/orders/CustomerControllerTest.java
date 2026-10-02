package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exercises the customer endpoints against the rows written by {@link SampleData}.
 */
@SpringBootTest
@Transactional
class CustomerControllerTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void rejectsPagingOutsideTheAllowedBounds() {
        assertBadRequest(() -> customerController.searchCustomers("Ada", 0, 0));
        assertBadRequest(() -> customerController.searchCustomers("Ada", 0, 101));
        assertBadRequest(() -> customerController.searchCustomers("Ada", -1, 20));
        assertBadRequest(() -> customerController.customerOrders(1L, 0, 0));
        assertBadRequest(() -> customerController.customerOrders(1L, 0, 101));
        assertBadRequest(() -> customerController.customerOrders(1L, -1, 20));
    }

    @Test
    void rejectsBlankSearchTerm() {
        assertBadRequest(() -> customerController.searchCustomers("   ", 0, 20));
    }

    @Test
    void findsCustomerByName() {
        var found = customerController.searchCustomers("Lovelace", 0, 20);
        assertEquals(List.of("ada@example.com"), found.stream().map(CustomerSummary::email).toList());
    }

    @Test
    void findsCustomerByEmail() {
        var found = customerController.searchCustomers("grace@example", 0, 20);
        assertEquals(List.of("Grace Hopper"), names(found));
    }

    @Test
    void searchIsOrderedByNameAndPaged() {
        assertEquals(List.of("Ada Lovelace", "Alan Turing"),
                names(customerController.searchCustomers("example.com", 0, 2)));
        assertEquals(List.of("Grace Hopper"), names(customerController.searchCustomers("example.com", 1, 2)));
    }

    @Test
    void searchTermIsBoundAndNotInterpretedAsSql() {
        assertEquals(List.of(), customerController.searchCustomers("' OR 1=1 --", 0, 20));
    }

    @Test
    void orderSummaryReportsLineCountAndTotal() {
        var graceId = customerId("Grace Hopper");
        var history = customerController.customerOrders(graceId, 0, 20);
        assertEquals(2, history.size());
        for (var summary : history) {
            assertEquals(2, summary.lineCount());
            // 2 notebooks at 450 plus 20 pens at 120 with the 5% bulk discount
            assertEquals(900 + 2280, summary.totalCents());
        }
    }

    @Test
    void unknownCustomerHasNoOrders() {
        assertEquals(List.of(), customerController.customerOrders(-1L, 0, 20));
    }

    private long customerId(String name) {
        return customerRepository.findAll().stream()
                .filter(customer -> name.equals(customer.getName()))
                .map(Customer::getId)
                .findFirst()
                .orElseThrow();
    }

    private static List<String> names(List<CustomerSummary> customers) {
        return customers.stream()
                .map(CustomerSummary::name)
                .toList();
    }

    private static void assertBadRequest(Executable call) {
        var exception = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
