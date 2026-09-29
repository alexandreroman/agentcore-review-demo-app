package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerControllerOrdersTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void eachOrderReportsTheCountAndTotalOfItsOwnLines() {
        var customer = customerRepository.save(new Customer("River Tam", "river@example.com"));
        var single = new Order(customer);
        single.addLine(new OrderLine("Notebook", 2, 450));
        var bulk = new Order(customer);
        bulk.addLine(new OrderLine("Pen", 10, 120));
        bulk.addLine(new OrderLine("Notebook", 1, 450));
        orderRepository.saveAll(List.of(single, bulk));

        var history = customerController.customerOrders(customer.getId(), 0, 20);

        assertEquals(2, history.size());
        var summaries = history.stream().collect(Collectors.toMap(OrderSummary::id, Function.identity()));
        var singleSummary = summaries.get(single.getId());
        assertEquals(1, singleSummary.lineCount());
        assertEquals(900, singleSummary.totalCents());
        var bulkSummary = summaries.get(bulk.getId());
        assertEquals(2, bulkSummary.lineCount());
        assertEquals(1590, bulkSummary.totalCents());
    }

    @Test
    void customerWithoutOrdersHasEmptyHistory() {
        var customer = customerRepository.save(new Customer("Simon Tam", "simon@example.com"));
        assertTrue(customerController.customerOrders(customer.getId(), 0, 20).isEmpty());
    }
}
