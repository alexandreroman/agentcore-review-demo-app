package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

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

    @Test
    void searchMatchesNamePrefix() {
        var customer = customerRepository.save(new Customer("Zelda Testerson", "zelda@search.example"));

        var found = customerController.searchCustomers("Zelda", 0, 20);

        assertEquals(List.of(new CustomerSummary(customer.getId(), "Zelda Testerson", "zelda@search.example")), found);
    }

    @Test
    void searchMatchesEmailPrefix() {
        customerRepository.save(new Customer("Zelda Testerson", "zelda@search.example"));

        var found = customerController.searchCustomers("zelda@search", 0, 20);

        assertEquals(List.of("Zelda Testerson"), found.stream().map(CustomerSummary::name).toList());
    }

    @Test
    void searchTreatsQuotesAsPlainText() {
        customerRepository.save(new Customer("Zelda Testerson", "zelda@search.example"));

        assertEquals(List.of(), customerController.searchCustomers("' OR '1'='1", 0, 20));
    }

    @Test
    void searchPagesByName() {
        customerRepository.save(new Customer("Zoe One", "zoe1@search.example"));
        customerRepository.save(new Customer("Zoe Two", "zoe2@search.example"));

        assertEquals(List.of("Zoe One"), customerController.searchCustomers("Zoe", 0, 1).stream()
                .map(CustomerSummary::name)
                .toList());
        assertEquals(List.of("Zoe Two"), customerController.searchCustomers("Zoe", 1, 1).stream()
                .map(CustomerSummary::name)
                .toList());
    }

    @Test
    void customerOrdersReturnsNewestFirstWithCountAndTotal() {
        var customer = customerRepository.save(new Customer("Zara Buyer", "zara@search.example"));
        var older = new Order(customer);
        older.addLine(new OrderLine("Notebook", 1, 450));
        var first = orderRepository.save(older);
        var newer = new Order(customer);
        newer.addLine(new OrderLine("Pen", 10, 120));
        newer.addLine(new OrderLine("Notebook", 2, 450));
        var second = orderRepository.save(newer);

        var history = customerController.customerOrders(customer.getId(), 0, 20);

        assertEquals(2, history.size());
        assertEquals(second.getId(), history.get(0).id());
        assertEquals(2, history.get(0).lineCount());
        assertEquals(1140 + 900, history.get(0).totalCents());
        assertEquals(first.getId(), history.get(1).id());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void customerOrdersPagesHistory() {
        var customer = customerRepository.save(new Customer("Zed Buyer", "zed@search.example"));
        var older = new Order(customer);
        older.addLine(new OrderLine("Notebook", 1, 450));
        var first = orderRepository.save(older);
        var newer = new Order(customer);
        newer.addLine(new OrderLine("Pen", 10, 120));
        var second = orderRepository.save(newer);

        assertEquals(List.of(second.getId()), customerController.customerOrders(customer.getId(), 0, 1).stream()
                .map(OrderSummary::id)
                .toList());
        assertEquals(List.of(first.getId()), customerController.customerOrders(customer.getId(), 1, 1).stream()
                .map(OrderSummary::id)
                .toList());
    }

    @Test
    void customerWithoutOrdersHasEmptyHistory() {
        var customer = customerRepository.save(new Customer("Zoltan Quiet", "zoltan@search.example"));

        assertEquals(List.of(), customerController.customerOrders(customer.getId(), 0, 20));
    }

    @Test
    void searchRejectsInvalidPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.searchCustomers("Zoe", 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.searchCustomers("Zoe", 0, 101)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.searchCustomers("Zoe", -1, 20)));
    }

    @Test
    void customerOrdersRejectsInvalidPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.customerOrders(1L, 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.customerOrders(1L, 0, 101)));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest(() -> customerController.customerOrders(1L, -1, 20)));
    }

    private static Object badRequest(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(ResponseStatusException.class, call).getStatusCode();
    }
}
