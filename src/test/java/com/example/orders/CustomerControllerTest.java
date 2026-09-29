package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void searchMatchesNameIgnoringCase() {
        var customer = customerRepository.save(new Customer("Zaphod Beeblebrox", "zaphod@example.com"));

        var found = customerController.searchCustomers("zaphod bee", 0, 20);

        assertEquals(List.of(new CustomerSummary(customer.getId(), "Zaphod Beeblebrox", "zaphod@example.com")), found);
    }

    @Test
    void searchMatchesEmailFragment() {
        var customer = customerRepository.save(new Customer("Trillian Astra", "trillian@heartofgold.example"));

        var found = customerController.searchCustomers("heartofgold", 0, 20);

        assertEquals(List.of(new CustomerSummary(customer.getId(), "Trillian Astra", "trillian@heartofgold.example")),
                found);
    }

    @Test
    void searchDoesNotTreatQuoteAsSql() {
        var found = customerController.searchCustomers("%' UNION SELECT id, name, email FROM customers--", 0, 20);

        assertEquals(List.of(), found);
    }

    @Test
    void customerOrdersReturnsNewestFirstWithLineCountsAndTotals() {
        var customer = customerRepository.save(new Customer("Ford Prefect", "ford@example.com"));
        var older = new Order(customer);
        older.addLine(new OrderLine("Notebook", 1, 450));
        var olderId = orderRepository.save(older).getId();
        var newer = new Order(customer);
        newer.addLine(new OrderLine("Notebook", 2, 450));
        newer.addLine(new OrderLine("Pen", 10, 120));
        var newerId = orderRepository.save(newer).getId();

        var history = customerController.customerOrders(customer.getId(), 0, 20);

        assertEquals(List.of(newerId, olderId), history.stream().map(OrderSummary::id).toList());
        assertEquals(2, history.get(0).lineCount());
        assertEquals(900 + 1140, history.get(0).totalCents());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void customerOrdersWithoutOrdersIsEmpty() {
        var customer = customerRepository.save(new Customer("Marvin", "marvin@example.com"));

        assertEquals(List.of(), customerController.customerOrders(customer.getId(), 0, 20));
    }

    @Test
    void searchRejectsOutOfBoundsPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.searchCustomers("a", 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.searchCustomers("a", 0, 101)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.searchCustomers("a", -1, 20)));
    }

    @Test
    void customerOrdersRejectsOutOfBoundsPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.customerOrders(1L, 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.customerOrders(1L, 0, 101)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.customerOrders(1L, -1, 20)));
    }

    private Object badRequest(Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run).getStatusCode();
    }
}
