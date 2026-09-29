package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class CustomerControllerTest {

    private static final Instant OLDER = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant NEWER = Instant.parse("2024-01-02T00:00:00Z");

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderLineRepository orderLineRepository = mock(OrderLineRepository.class);
    private final CustomerController controller =
            new CustomerController(customerRepository, orderRepository, orderLineRepository);

    @Test
    void searchBindsTheRawQueryTextAndPagesByName() {
        var injection = "%' UNION SELECT id, password, email FROM users --";
        var ada = new CustomerSummary(1L, "Ada Lovelace", "ada@example.com");
        when(customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(eq(injection), eq(injection),
                any(Pageable.class))).thenReturn(List.of(ada));

        assertEquals(List.of(ada), controller.searchCustomers(injection, 2, 5));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(eq(injection),
                eq(injection), pageable.capture());
        assertEquals(PageRequest.of(2, 5, Sort.by("name")), pageable.getValue());
    }

    @Test
    void orderHistoryReadsAllLinesWithASingleQuery() {
        var newer = order(20L, NEWER);
        var older = order(10L, OLDER);
        when(orderRepository.findByCustomerId(eq(7L), any(Pageable.class))).thenReturn(List.of(newer, older));
        when(orderLineRepository.findByOrderIdIn(List.of(20L, 10L)))
                .thenReturn(List.of(line(newer, 1, 450), line(older, 1, 450), line(older, 10, 120)));

        var history = controller.customerOrders(7L, 0, 20);

        assertEquals(List.of(new OrderSummary(20L, NEWER, 1, 450), new OrderSummary(10L, OLDER, 2, 450 + 1140)),
                history);
        verify(orderLineRepository, never()).findByOrderId(any());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerId(eq(7L), pageable.capture());
        assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")), pageable.getValue());
    }

    @Test
    void orderHistoryOfACustomerWithoutOrdersQueriesNoLines() {
        when(orderRepository.findByCustomerId(eq(7L), any(Pageable.class))).thenReturn(List.of());

        assertEquals(List.of(), controller.customerOrders(7L, 0, 20));

        verify(orderLineRepository, never()).findByOrderIdIn(any());
    }

    @Test
    void bothEndpointsDeclareTheSamePageAndSizeBounds() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator().forExecutables();

            var search = CustomerController.class.getDeclaredMethod("searchCustomers", String.class, int.class,
                    int.class);
            assertEquals(1, validator.validateParameters(controller, search, new Object[] {"ada", 0, 0}).size());
            assertEquals(1, validator.validateParameters(controller, search, new Object[] {"ada", 0, 101}).size());
            assertEquals(1, validator.validateParameters(controller, search, new Object[] {"ada", -1, 20}).size());
            assertEquals(0, validator.validateParameters(controller, search, new Object[] {"ada", 0, 100}).size());

            var orders = CustomerController.class.getDeclaredMethod("customerOrders", long.class, int.class, int.class);
            assertEquals(1, validator.validateParameters(controller, orders, new Object[] {7L, 0, 0}).size());
            assertEquals(1, validator.validateParameters(controller, orders, new Object[] {7L, 0, 101}).size());
            assertEquals(1, validator.validateParameters(controller, orders, new Object[] {7L, -1, 20}).size());
            assertEquals(0, validator.validateParameters(controller, orders, new Object[] {7L, 0, 100}).size());
        }
    }

    private static Order order(long id, Instant createdAt) {
        var order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getCreatedAt()).thenReturn(createdAt);
        return order;
    }

    private static OrderLine line(Order order, int quantity, long unitPriceCents) {
        var line = mock(OrderLine.class);
        when(line.getOrder()).thenReturn(order);
        when(line.getQuantity()).thenReturn(quantity);
        when(line.getUnitPriceCents()).thenReturn(unitPriceCents);
        return line;
    }
}
