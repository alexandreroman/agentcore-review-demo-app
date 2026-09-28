package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerControllerTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderLineRepository orderLineRepository = mock(OrderLineRepository.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CustomerController(customerRepository, orderRepository, orderLineRepository))
            .build();

    @Test
    void searchUsesDefaultPagingAndNeverReturnsTheEmailAddress() throws Exception {
        when(customerRepository.findByNameStartingWithIgnoreCaseOrEmailIgnoreCase(any(), any(), any()))
                .thenReturn(List.of(customer(7L, "Ada Lovelace", "ada@example.com")));

        mockMvc.perform(get("/customers/search").param("q", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findByNameStartingWithIgnoreCaseOrEmailIgnoreCase(eq("ada"), eq("ada"),
                pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
    }

    @Test
    void searchTrimsTheTerm() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "  ad  "))
                .andExpect(status().isOk());

        verify(customerRepository).findByNameStartingWithIgnoreCaseOrEmailIgnoreCase(eq("ad"), eq("ad"), any());
    }

    @Test
    void searchRejectsTermsThatAreTooShortOrTooLong() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", " a "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/search").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerRepository);
    }

    @Test
    void searchRejectsMissingTerm() throws Exception {
        mockMvc.perform(get("/customers/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchRejectsPagingOutsideTheAllowedBounds() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "ada").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/search").param("q", "ada").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/search").param("q", "ada").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerRepository);
    }

    @Test
    void searchStopsAtTheResultWindow() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "ada").param("page", "49").param("size", "20"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/customers/search").param("q", "ada").param("page", "50").param("size", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderHistoryReportsLineCountAndTotal() throws Exception {
        var order = order(5L);
        when(orderRepository.findByCustomerId(eq(1L), any())).thenReturn(List.of(order));
        when(orderLineRepository.findByOrderIdIn(List.of(5L)))
                .thenReturn(List.of(line(order, "Notebook", 2, 450), line(order, "Pen", 10, 120)));

        mockMvc.perform(get("/customers/1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].lineCount").value(2))
                .andExpect(jsonPath("$[0].totalCents").value(900 + 1140));
    }

    @Test
    void orderHistoryWithoutOrdersDoesNotQueryLines() throws Exception {
        when(orderRepository.findByCustomerId(eq(9L), any())).thenReturn(List.of());

        mockMvc.perform(get("/customers/9/orders"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verifyNoInteractions(orderLineRepository);
    }

    @Test
    void orderHistoryRejectsPagingOutsideTheAllowedBounds() throws Exception {
        mockMvc.perform(get("/customers/1/orders").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/1/orders").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/1/orders").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderRepository);
    }

    private static Customer customer(long id, String name, String email) {
        var customer = new Customer(name, email);
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private static Order order(long id) {
        var order = new Order(customer(1L, "Ada Lovelace", "ada@example.com"));
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2024-01-01T00:00:00Z"));
        return order;
    }

    private static OrderLine line(Order order, String product, int quantity, long unitPriceCents) {
        var line = new OrderLine(product, quantity, unitPriceCents);
        line.setOrder(order);
        return line;
    }
}
