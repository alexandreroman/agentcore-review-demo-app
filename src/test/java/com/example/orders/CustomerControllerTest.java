package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

    private String suffix;
    private long customerId;
    private long oldestOrderId;
    private long newestOrderId;

    @BeforeEach
    void createCustomerWithTwoOrders() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customerRepository.save(
                new Customer("Zoe Quantum " + suffix, "zoe." + suffix + "@example.org"));
        customerId = customer.getId();

        var oldest = new Order(customer);
        oldest.addLine(new OrderLine("Notebook", 1, 450));
        oldestOrderId = orderRepository.save(oldest).getId();

        var newest = new Order(customer);
        newest.addLine(new OrderLine("Pen", 10, 120));
        newest.addLine(new OrderLine("Notebook", 2, 450));
        newestOrderId = orderRepository.save(newest).getId();
    }

    @Test
    void searchMatchesNameIgnoringCase() {
        var found = customerController.searchCustomers("quantum " + suffix, 0, 20);

        assertEquals(1, found.size());
        assertEquals(customerId, found.get(0).id());
        assertEquals("Zoe Quantum " + suffix, found.get(0).name());
        assertEquals("zoe." + suffix + "@example.org", found.get(0).email());
    }

    @Test
    void searchMatchesEmailFragment() {
        var found = customerController.searchCustomers("ZOE." + suffix + "@", 0, 20);

        assertEquals(1, found.size());
        assertEquals(customerId, found.get(0).id());
    }

    @Test
    void searchTreatsQuoteAsPlainTextInsteadOfSql() {
        var found = customerController.searchCustomers("' UNION SELECT id, name, email FROM customers --", 0, 20);

        assertTrue(found.isEmpty());
    }

    @Test
    void searchRejectsOutOfRangeSizeAndNegativePage() {
        var tooLarge = assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("zoe", 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, tooLarge.getStatusCode());

        var tooSmall = assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("zoe", 0, 0));
        assertEquals(HttpStatus.BAD_REQUEST, tooSmall.getStatusCode());

        var negativePage = assertThrows(ResponseStatusException.class,
                () -> customerController.searchCustomers("zoe", -1, 20));
        assertEquals(HttpStatus.BAD_REQUEST, negativePage.getStatusCode());
    }

    @Test
    void customerOrdersReturnsNewestFirstWithLineCountAndTotal() {
        var history = customerController.customerOrders(customerId, 0, 20);

        assertEquals(2, history.size());
        assertEquals(newestOrderId, history.get(0).id());
        assertEquals(2, history.get(0).lineCount());
        assertEquals(1140 + 900, history.get(0).totalCents());
        assertEquals(oldestOrderId, history.get(1).id());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void customerOrdersPagesThroughTheHistory() {
        var secondPage = customerController.customerOrders(customerId, 1, 1);

        assertEquals(1, secondPage.size());
        assertEquals(oldestOrderId, secondPage.get(0).id());
        assertEquals(450, secondPage.get(0).totalCents());
    }

    @Test
    void customerOrdersRejectsOutOfRangeSizeAndNegativePage() {
        var tooLarge = assertThrows(ResponseStatusException.class,
                () -> customerController.customerOrders(customerId, 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, tooLarge.getStatusCode());

        var negativePage = assertThrows(ResponseStatusException.class,
                () -> customerController.customerOrders(customerId, -1, 20));
        assertEquals(HttpStatus.BAD_REQUEST, negativePage.getStatusCode());
    }

    @Test
    void customerWithoutOrdersHasEmptyHistory() {
        var other = customerRepository.save(
                new Customer("No Orders " + suffix, "none." + suffix + "@example.org"));

        assertTrue(customerController.customerOrders(other.getId(), 0, 20).isEmpty());
    }
}
