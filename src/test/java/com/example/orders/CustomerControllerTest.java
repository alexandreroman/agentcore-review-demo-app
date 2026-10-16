package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

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
    void searchMatchesNameOrEmailIgnoringCase() {
        var zoe = customerRepository.save(new Customer("Zoe Zebra", "zoe@search.example"));

        var byName = customerController.searchCustomers("ZEBRA", 0, 20);
        var byEmail = customerController.searchCustomers("zoe@search", 0, 20);

        assertEquals(List.of(new CustomerSummary(zoe.getId(), "Zoe Zebra", "zoe@search.example")), byName);
        assertEquals(byName, byEmail);
    }

    @Test
    void searchQuotesAreTreatedAsPlainText() {
        customerRepository.save(new Customer("Zoe Zebra", "zoe@search.example"));

        var matches = customerController.searchCustomers("' UNION SELECT id, name, email FROM customers --", 0, 20);

        assertEquals(List.of(), matches);
    }

    @Test
    void searchPaginatesOrderedByName() {
        var yolanda = customerRepository.save(new Customer("Yolanda Yak", "yolanda@search.example"));
        var yves = customerRepository.save(new Customer("Yves Yak", "yves@search.example"));

        var firstPage = customerController.searchCustomers("Yak", 0, 1);
        var secondPage = customerController.searchCustomers("Yak", 1, 1);

        assertEquals(List.of(yolanda.getId()), firstPage.stream().map(CustomerSummary::id).toList());
        assertEquals(List.of(yves.getId()), secondPage.stream().map(CustomerSummary::id).toList());
    }

    @Test
    void customerOrdersAreNewestFirstWithLineCountsAndTotals() {
        var customer = customerRepository.save(new Customer("Xena Order", "xena@orders.example"));
        var older = new Order(customer);
        older.addLine(new OrderLine("Notebook", 1, 450));
        orderRepository.save(older);
        var newer = new Order(customer);
        newer.addLine(new OrderLine("Notebook", 2, 450));
        newer.addLine(new OrderLine("Pen", 10, 120));
        orderRepository.save(newer);

        var history = customerController.customerOrders(customer.getId(), 0, 20);

        assertEquals(2, history.size());
        assertEquals(newer.getId(), history.get(0).id());
        assertEquals(2, history.get(0).lineCount());
        assertEquals(900 + 1140, history.get(0).totalCents());
        assertEquals(older.getId(), history.get(1).id());
        assertEquals(1, history.get(1).lineCount());
        assertEquals(450, history.get(1).totalCents());
    }

    @Test
    void customerWithoutOrdersHasEmptyHistory() {
        var customer = customerRepository.save(new Customer("Wendy Without", "wendy@orders.example"));

        assertEquals(List.of(), customerController.customerOrders(customer.getId(), 0, 20));
    }

    @Test
    void outOfRangePaginationParametersAreRejected() {
        assertBadRequest(() -> customerController.searchCustomers("ada", 0, 0));
        assertBadRequest(() -> customerController.searchCustomers("ada", 0, 101));
        assertBadRequest(() -> customerController.searchCustomers("ada", -1, 20));
        assertBadRequest(() -> customerController.customerOrders(1L, 0, 0));
        assertBadRequest(() -> customerController.customerOrders(1L, 0, 101));
        assertBadRequest(() -> customerController.customerOrders(1L, -1, 20));
    }

    private static void assertBadRequest(Executable call) {
        var exception = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
