package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerControllerTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

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
}
