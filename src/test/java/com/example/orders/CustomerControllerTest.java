package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class CustomerControllerTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Customer customer;

    @BeforeEach
    void insertCustomerWithTwoOrders() {
        customer = customerRepository.save(new Customer("Zoe Washburne", "zoe@example.org"));
        var older = new Order(customer);
        older.addLine(new OrderLine("Notebook", 1, 450));
        orderRepository.save(older);
        var newer = new Order(customer);
        newer.addLine(new OrderLine("Pen", 10, 120));
        newer.addLine(new OrderLine("Eraser", 2, 50));
        orderRepository.save(newer);
    }

    @Test
    void searchMatchesNameFragmentIgnoringCase() {
        assertEquals(List.of(new CustomerSummary(customer.getId(), "Zoe Washburne", "zoe@example.org")),
                customerController.searchCustomers("WASHBURNE", 0, 20));
    }

    @Test
    void searchMatchesEmailFragment() {
        assertEquals(List.of(new CustomerSummary(customer.getId(), "Zoe Washburne", "zoe@example.org")),
                customerController.searchCustomers("zoe@example.org", 0, 20));
    }

    @Test
    void searchTreatsQuotesAsOrdinaryCharacters() {
        assertEquals(List.of(), customerController.searchCustomers("' OR '1'='1", 0, 20));
    }

    @Test
    void searchRejectsInvalidPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("zoe", 0, 0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("zoe", 0, 101)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("zoe", -1, 20)).getStatusCode());
    }

    @Test
    void customerOrdersSummarisesNewestOrderFirst() {
        var history = customerController.customerOrders(customer.getId(), 0, 20);

        assertEquals(2, history.size());
        assertEquals(2, history.get(0).lineCount());
        assertEquals(1140 + 100, history.get(0).totalCents());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void customerOrdersPagesTheHistory() {
        var secondPage = customerController.customerOrders(customer.getId(), 1, 1);

        assertEquals(1, secondPage.size());
        assertEquals(450, secondPage.get(0).totalCents());
    }

    @Test
    void customerOrdersOfUnknownCustomerIsEmpty() {
        assertEquals(List.of(), customerController.customerOrders(-1L, 0, 20));
    }

    @Test
    void customerOrdersRejectsInvalidPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> customerController.customerOrders(customer.getId(), 0, 101)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> customerController.customerOrders(customer.getId(), -1, 20)).getStatusCode());
    }
}
