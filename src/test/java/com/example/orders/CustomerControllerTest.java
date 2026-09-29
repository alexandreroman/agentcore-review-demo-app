package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exercises the customer endpoints against the sample data loaded at startup: the second sample customer
 * ("Grace Hopper") has two orders, each with 2 notebooks at 450 cents and 20 pens at 120 cents.
 */
@SpringBootTest
class CustomerControllerTest {

    private static final long NOTEBOOK_LINE_CENTS = 2 * 450;
    private static final long PEN_LINE_CENTS = 20 * 120 * (100 - Pricing.BULK_DISCOUNT_PERCENT) / 100;

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void searchMatchesNameOrEmail() {
        var byName = customerController.searchCustomers("Lovelace", 0, 20);
        assertEquals(List.of("ada@example.com"), byName.stream().map(CustomerSummary::email).toList());

        var byEmail = customerController.searchCustomers("grace@", 0, 20);
        assertEquals(List.of("Grace Hopper"), byEmail.stream().map(CustomerSummary::name).toList());
    }

    @Test
    void searchTreatsQuotesAsPlainText() {
        var injected = customerController.searchCustomers("%' UNION SELECT id, name, email FROM customers -- ", 0, 20);
        assertEquals(List.of(), injected);
    }

    @Test
    void searchHonoursPaging() {
        var firstPage = customerController.searchCustomers("example.com", 0, 1);
        assertEquals(1, firstPage.size());
        assertEquals("Ada Lovelace", firstPage.getFirst().name());

        var secondPage = customerController.searchCustomers("example.com", 1, 1);
        assertEquals(1, secondPage.size());
        assertEquals("Alan Turing", secondPage.getFirst().name());
    }

    @Test
    void orderHistorySummarisesEveryOrderOfThePage() {
        var history = customerController.customerOrders(customerId("Grace Hopper"), 0, 20);

        assertEquals(2, history.size());
        for (var summary : history) {
            assertEquals(2, summary.lineCount());
            assertEquals(NOTEBOOK_LINE_CENTS + PEN_LINE_CENTS, summary.totalCents());
        }
    }

    @Test
    void orderHistoryOfUnknownCustomerIsEmpty() {
        assertEquals(List.of(), customerController.customerOrders(-1, 0, 20));
    }

    @Test
    void invalidPagingIsRejected() {
        assertBadRequest(() -> customerController.searchCustomers("Ada", 0, 0));
        assertBadRequest(() -> customerController.searchCustomers("Ada", 0, 101));
        assertBadRequest(() -> customerController.searchCustomers("Ada", -1, 20));
        assertBadRequest(() -> customerController.customerOrders(1, 0, 0));
        assertBadRequest(() -> customerController.customerOrders(1, 0, 101));
        assertBadRequest(() -> customerController.customerOrders(1, -1, 20));
    }

    private static void assertBadRequest(Executable call) {
        var exception = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private long customerId(String name) {
        return customerRepository.findAll().stream()
                .filter(customer -> name.equals(customer.getName()))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
