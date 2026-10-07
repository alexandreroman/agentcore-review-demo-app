package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
class CustomerControllerTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    private CustomerController controller;
    private Customer ada;
    private Customer grace;
    private Order olderOrder;
    private Order newerOrder;

    @BeforeEach
    void saveCustomersAndOrders() {
        controller = new CustomerController(customerRepository, orderRepository);
        ada = customerRepository.save(new Customer("Ada Lovelace", "ada@example.com"));
        grace = customerRepository.save(new Customer("Grace Hopper", "grace@example.com"));
        var older = new Order(ada);
        older.addLine(new OrderLine("Notebook", 1, 450));
        olderOrder = orderRepository.save(older);
        var newer = new Order(ada);
        newer.addLine(new OrderLine("Notebook", 1, 450));
        newer.addLine(new OrderLine("Pen", 10, 120));
        newerOrder = orderRepository.save(newer);
    }

    private static HttpStatusCode statusOf(Executable call) {
        return assertThrows(ResponseStatusException.class, call).getStatusCode();
    }

    @Test
    void searchMatchesNameAndEmail() {
        assertEquals(List.of("Ada Lovelace"),
                controller.searchCustomers("ADA", 0, 20).stream().map(CustomerSummary::name).toList());
        assertEquals(List.of("Grace Hopper"),
                controller.searchCustomers("GRACE@example.com", 0, 20).stream().map(CustomerSummary::name).toList());
    }

    @Test
    void searchPagesMatchesByName() {
        assertEquals(List.of("Ada Lovelace"),
                controller.searchCustomers("example.com", 0, 1).stream().map(CustomerSummary::name).toList());
        assertEquals(List.of("Grace Hopper"),
                controller.searchCustomers("example.com", 1, 1).stream().map(CustomerSummary::name).toList());
    }

    @Test
    void searchRejectsInvalidPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.searchCustomers("ada", 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.searchCustomers("ada", 0, 101)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.searchCustomers("ada", -1, 20)));
    }

    @Test
    void customerOrdersRejectsInvalidPaging() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.customerOrders(ada.getId(), 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.customerOrders(ada.getId(), 0, 101)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.customerOrders(ada.getId(), -1, 20)));
    }

    @Test
    void customerOrdersReturnsNewestFirstWithLineCountAndTotal() {
        var history = controller.customerOrders(ada.getId(), 0, 20);

        assertEquals(List.of(newerOrder.getId(), olderOrder.getId()), history.stream().map(OrderSummary::id).toList());
        assertEquals(2, history.get(0).lineCount());
        assertEquals(Pricing.orderTotal(newerOrder.getLines()), history.get(0).totalCents());
        assertEquals(450 + 1140, history.get(0).totalCents());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void customerOrdersAppliesPaging() {
        assertEquals(List.of(olderOrder.getId()),
                controller.customerOrders(ada.getId(), 1, 1).stream().map(OrderSummary::id).toList());
    }

    @Test
    void customerWithoutOrdersHasEmptyHistory() {
        assertTrue(controller.customerOrders(grace.getId(), 0, 20).isEmpty());
    }

    @Test
    void unknownCustomerIsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.customerOrders(ada.getId() + 1000, 0, 20)));
    }
}
