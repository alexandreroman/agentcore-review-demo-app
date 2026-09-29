package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerControllerSearchTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void searchFindsCustomerByEmailFragment() {
        var customer = customerRepository.save(new Customer("Zoe Washburne", "zoe@example.com"));
        assertEquals(List.of(new CustomerSummary(customer.getId(), "Zoe Washburne", "zoe@example.com")),
                customerController.searchCustomers("zoe@", 0, 20));
    }

    @Test
    void searchTreatsQuotesInQueryAsLiteralText() {
        customerRepository.save(new Customer("Malcolm Reynolds", "mal@example.com"));
        assertTrue(customerController.searchCustomers("' OR '1'='1", 0, 20).isEmpty());
    }
}
