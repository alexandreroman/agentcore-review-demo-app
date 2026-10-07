package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@DataJpaTest
class CustomerRepositoryTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void saveCustomers() {
        customerRepository.save(new Customer("Ada Lovelace", "ada@example.com"));
        customerRepository.save(new Customer("Grace Hopper", "grace@example.com"));
    }

    private List<Customer> search(String q) {
        return customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, FIRST_PAGE);
    }

    @Test
    void matchesNameOrEmailIgnoringCase() {
        assertEquals(List.of("Ada Lovelace"), search("ADA").stream().map(Customer::getName).toList());
        assertEquals(List.of("Grace Hopper"), search("GRACE@example.com").stream().map(Customer::getName).toList());
    }

    @Test
    void treatsSqlMetaCharactersAsPlainText() {
        assertTrue(search("' UNION SELECT id, name, email FROM customers --").isEmpty());
        assertTrue(search("'").isEmpty());
    }
}
