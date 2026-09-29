package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class CustomerOrdersTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Customer customer;
    private Order oneLineOrder;
    private Order twoLineOrder;

    @BeforeEach
    void insertCustomerWithTwoOrders() {
        customer = customerRepository.saveAndFlush(new Customer("Page Turner", "page.turner@example.com"));
        oneLineOrder = new Order(customer);
        oneLineOrder.addLine(new OrderLine("Notebook", 1, 450));
        orderRepository.saveAndFlush(oneLineOrder);
        twoLineOrder = new Order(customer);
        twoLineOrder.addLine(new OrderLine("Notebook", 2, 450));
        twoLineOrder.addLine(new OrderLine("Pen", 10, 120));
        orderRepository.saveAndFlush(twoLineOrder);
    }

    @Test
    void historyReportsLineCountAndTotalOfEveryOrderNewestFirst() {
        var history = customerController.customerOrders(customer.getId(), 0, 20);
        assertEquals(List.of(twoLineOrder.getId(), oneLineOrder.getId()), ids(history));
        assertEquals(2, history.get(0).lineCount());
        assertEquals(900 + 1140, history.get(0).totalCents());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void historyIsPaged() {
        assertEquals(List.of(twoLineOrder.getId()), ids(customerController.customerOrders(customer.getId(), 0, 1)));
        assertEquals(List.of(oneLineOrder.getId()), ids(customerController.customerOrders(customer.getId(), 1, 1)));
        assertEquals(List.of(), ids(customerController.customerOrders(customer.getId(), 2, 1)));
    }

    @Test
    void customerWithoutOrdersHasEmptyHistory() {
        var other = customerRepository.saveAndFlush(new Customer("No Orders", "no.orders@example.com"));
        assertEquals(List.of(), customerController.customerOrders(other.getId(), 0, 20));
    }

    @Test
    void sizeAndPageOutOfRangeAreRejected() {
        var customerId = customer.getId();
        assertThrows(ResponseStatusException.class, () -> customerController.customerOrders(customerId, 0, 0));
        assertThrows(ResponseStatusException.class, () -> customerController.customerOrders(customerId, 0, 101));
        assertThrows(ResponseStatusException.class, () -> customerController.customerOrders(customerId, -1, 20));
    }

    private static List<Long> ids(List<OrderSummary> history) {
        return history.stream().map(OrderSummary::id).toList();
    }
}
