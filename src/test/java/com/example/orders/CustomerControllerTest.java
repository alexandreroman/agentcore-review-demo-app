package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class CustomerControllerTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderLineRepository orderLineRepository = mock(OrderLineRepository.class);
    private final CustomerController controller =
            new CustomerController(customerRepository, orderRepository, orderLineRepository);

    private static Customer customer(long id, String name, String email) {
        var customer = new Customer(name, email);
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private static Order order(Customer customer, long id, Instant createdAt) {
        var order = new Order(customer);
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        return order;
    }

    private static OrderLine line(Order order, String product, int quantity, long unitPriceCents) {
        var line = new OrderLine(product, quantity, unitPriceCents);
        line.setOrder(order);
        return line;
    }

    @Test
    void searchPassesTheQueryAsAParameterAndMapsSummaries() {
        var pageRequest = PageRequest.of(1, 10, Sort.by("name"));
        when(customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase("ada", "ada", pageRequest))
                .thenReturn(List.of(customer(7, "Ada Lovelace", "ada@example.com")));

        assertEquals(List.of(new CustomerSummary(7L, "Ada Lovelace", "ada@example.com")),
                controller.searchCustomers("ada", 1, 10));
    }

    @Test
    void searchTreatsSqlSyntaxInTheQueryAsPlainText() {
        var injection = "' UNION SELECT id, password, email FROM users --";
        var pageRequest = PageRequest.of(0, 20, Sort.by("name"));
        when(customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(injection, injection,
                pageRequest)).thenReturn(List.of());

        assertTrue(controller.searchCustomers(injection, 0, 20).isEmpty());
        verify(customerRepository)
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(injection, injection, pageRequest);
    }

    @Test
    void searchRejectsInvalidPagination() {
        assertBadRequest("size must be between 1 and 100",
                assertThrows(ResponseStatusException.class, () -> controller.searchCustomers("ada", 0, 0)));
        assertBadRequest("size must be between 1 and 100",
                assertThrows(ResponseStatusException.class, () -> controller.searchCustomers("ada", 0, 101)));
        assertBadRequest("page must not be negative",
                assertThrows(ResponseStatusException.class, () -> controller.searchCustomers("ada", -1, 20)));
        verifyNoInteractions(customerRepository);
    }

    @Test
    void customerOrdersFetchesTheLinesOfThePageInOneQuery() {
        var customer = customer(5, "Ada Lovelace", "ada@example.com");
        var older = order(customer, 1, Instant.parse("2024-01-01T00:00:00Z"));
        var newer = order(customer, 2, Instant.parse("2024-01-02T00:00:00Z"));
        var pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(orderRepository.findByCustomerId(5L, pageRequest)).thenReturn(List.of(newer, older));
        when(orderLineRepository.findByOrderIdIn(List.of(2L, 1L))).thenReturn(List.of(
                line(newer, "Pen", 10, 120),
                line(older, "Notebook", 1, 450),
                line(older, "Pen", 2, 120)));

        var history = controller.customerOrders(5, 0, 20);

        assertEquals(List.of(
                new OrderSummary(2L, Instant.parse("2024-01-02T00:00:00Z"), 1, 1140),
                new OrderSummary(1L, Instant.parse("2024-01-01T00:00:00Z"), 2, 450 + 240)),
                history);
        verify(orderLineRepository, times(1)).findByOrderIdIn(List.of(2L, 1L));
    }

    @Test
    void customerOrdersWithoutOrdersDoesNotQueryLines() {
        when(orderRepository.findByCustomerId(5L, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"))))
                .thenReturn(List.of());

        assertTrue(controller.customerOrders(5, 0, 20).isEmpty());
        verifyNoInteractions(orderLineRepository);
    }

    @Test
    void customerOrdersRejectsInvalidPagination() {
        assertBadRequest("size must be between 1 and 100",
                assertThrows(ResponseStatusException.class, () -> controller.customerOrders(5, 0, 101)));
        assertBadRequest("page must not be negative",
                assertThrows(ResponseStatusException.class, () -> controller.customerOrders(5, -1, 20)));
        verifyNoInteractions(orderRepository, orderLineRepository);
    }

    private static void assertBadRequest(String reason, ResponseStatusException exception) {
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(reason, exception.getReason());
    }
}
