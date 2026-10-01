package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

class CustomerControllerTest {

    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec statementSpec = mock(JdbcClient.StatementSpec.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderLineRepository orderLineRepository = mock(OrderLineRepository.class);

    private final CustomerController controller =
            new CustomerController(jdbcClient, orderRepository, orderLineRepository);

    @Test
    void searchBindsTheQueryStringAsParameter() {
        var found = new CustomerSummary(1L, "Ada Lovelace", "ada@example.com");
        stubSearch(List.of(found));
        var injection = "' UNION SELECT id, password, email FROM users --";

        assertEquals(List.of(found), controller.searchCustomers(injection, 1, 10));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sql.capture());
        assertFalse(sql.getValue().contains(injection));
        assertTrue(sql.getValue().contains(":pattern"));
        verify(statementSpec).param("pattern", "%" + injection + "%");
        verify(statementSpec).param("limit", 10);
        verify(statementSpec).param("offset", 10L);
    }

    @Test
    void searchRejectsPagingOutsideTheAllowedRange() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.searchCustomers("ada", 0, 0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.searchCustomers("ada", 0, 101)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.searchCustomers("ada", -1, 20)).getStatusCode());
    }

    @Test
    void customerOrdersRejectsPagingOutsideTheAllowedRange() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.customerOrders(1L, 0, 0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.customerOrders(1L, 0, 101)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.customerOrders(1L, -1, 20)).getStatusCode());
    }

    @Test
    void customerOrdersLoadsEveryLineOfThePageInOneQuery() {
        var newest = Instant.parse("2024-01-02T00:00:00Z");
        var oldest = Instant.parse("2024-01-01T00:00:00Z");
        when(orderRepository.findByCustomerId(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(order(7L, newest), order(8L, oldest)));
        when(orderLineRepository.findByOrderIdIn(List.of(7L, 8L)))
                .thenReturn(List.of(line(7L, 1, 450), line(7L, 10, 120), line(8L, 2, 500)));

        var history = controller.customerOrders(1L, 0, 20);

        assertEquals(List.of(new OrderSummary(7L, newest, 2, 450 + 1140), new OrderSummary(8L, oldest, 1, 1000)),
                history);
        verify(orderLineRepository).findByOrderIdIn(List.of(7L, 8L));
    }

    @Test
    void customerOrdersAsksForANewestFirstPageAndSkipsLinesWhenEmpty() {
        when(orderRepository.findByCustomerId(eq(1L), any(Pageable.class))).thenReturn(List.of());

        assertEquals(List.of(), controller.customerOrders(1L, 2, 5));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerId(eq(1L), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "id"), pageable.getValue().getSort());
        verify(orderLineRepository, never()).findByOrderIdIn(any());
    }

    @SuppressWarnings("unchecked")
    private void stubSearch(List<CustomerSummary> result) {
        var querySpec = (JdbcClient.MappedQuerySpec<CustomerSummary>) mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any(Object.class))).thenReturn(statementSpec);
        when(statementSpec.query(CustomerSummary.class)).thenReturn(querySpec);
        when(querySpec.list()).thenReturn(result);
    }

    private static Order order(long id, Instant createdAt) {
        var order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getCreatedAt()).thenReturn(createdAt);
        return order;
    }

    private static OrderLine line(long orderId, int quantity, long unitPriceCents) {
        var line = mock(OrderLine.class);
        when(line.getOrderId()).thenReturn(orderId);
        when(line.getQuantity()).thenReturn(quantity);
        when(line.getUnitPriceCents()).thenReturn(unitPriceCents);
        return line;
    }
}
