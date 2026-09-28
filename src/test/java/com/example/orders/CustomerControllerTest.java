package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerControllerTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void searchFindsCustomerByNameFragment() {
        customerRepository.save(new Customer("Zoe Quotation", "zoe@example.com"));
        var results = customerController.searchCustomers("Quotation", 0, 20);
        assertEquals(1, results.size());
        assertEquals("zoe@example.com", results.get(0).email());
    }

    @Test
    void searchTreatsQuotesAsLiteralText() {
        assertTrue(customerController.searchCustomers("' OR '1'='1", 0, 20).isEmpty());
        assertTrue(customerController.searchCustomers("' UNION SELECT id, name, email FROM customers --", 0, 20)
                .isEmpty());
    }

    @Test
    void customerOrdersSummarisesEveryOrderOfThePage() {
        var customer = customerRepository.save(new Customer("Nina Batch", "nina@example.com"));
        var first = new Order(customer);
        first.addLine(new OrderLine("Notebook", 2, 450));
        first = orderRepository.save(first);
        var second = new Order(customer);
        second.addLine(new OrderLine("Pen", 10, 120));
        second.addLine(new OrderLine("Eraser", 1, 90));
        second = orderRepository.save(second);

        var history = customerController.customerOrders(customer.getId(), 0, 20);

        assertEquals(2, history.size());
        var summariesById = history.stream().collect(Collectors.toMap(OrderSummary::id, Function.identity()));
        assertEquals(1, summariesById.get(first.getId()).lineCount());
        assertEquals(900, summariesById.get(first.getId()).totalCents());
        assertEquals(2, summariesById.get(second.getId()).lineCount());
        assertEquals(1230, summariesById.get(second.getId()).totalCents());
    }

    @Test
    void customerOrdersIsEmptyForUnknownCustomer() {
        assertTrue(customerController.customerOrders(-1L, 0, 20).isEmpty());
    }
}
