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
class CustomerSearchTest {

    @Autowired
    private CustomerController customerController;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void insertCustomersWithSpecialCharacters() {
        customerRepository.saveAndFlush(new Customer("O'Brien %Ltd", "o.brien@example.com"));
        customerRepository.saveAndFlush(new Customer("Under_score Ltd", "under.score@example.com"));
    }

    @Test
    void quotesInTheQueryAreDataAndNotSql() {
        assertEquals(List.of(), customerController.searchCustomers("' OR '1'='1", 0, 20));
        assertEquals(List.of("O'Brien %Ltd"), names(customerController.searchCustomers("O'Brien", 0, 20)));
    }

    @Test
    void likeWildcardsInTheQueryMatchLiterally() {
        assertEquals(List.of("O'Brien %Ltd"), names(customerController.searchCustomers("%", 0, 20)));
        assertEquals(List.of("Under_score Ltd"), names(customerController.searchCustomers("_", 0, 20)));
    }

    @Test
    void emailIsSearchedAsWell() {
        assertEquals(List.of("Under_score Ltd"), names(customerController.searchCustomers("under.score@", 0, 20)));
    }

    @Test
    void resultsArePagedAndOrderedByName() {
        assertEquals(List.of("O'Brien %Ltd"), names(customerController.searchCustomers("Ltd", 0, 1)));
        assertEquals(List.of("Under_score Ltd"), names(customerController.searchCustomers("Ltd", 1, 1)));
        assertEquals(List.of(), names(customerController.searchCustomers("Ltd", 2, 1)));
    }

    @Test
    void overlyLongQueryIsRejected() {
        assertThrows(ResponseStatusException.class, () -> customerController.searchCustomers("a".repeat(101), 0, 20));
    }

    @Test
    void sizeAndPageOutOfRangeAreRejected() {
        assertThrows(ResponseStatusException.class, () -> customerController.searchCustomers("Ltd", 0, 0));
        assertThrows(ResponseStatusException.class, () -> customerController.searchCustomers("Ltd", 0, 101));
        assertThrows(ResponseStatusException.class, () -> customerController.searchCustomers("Ltd", -1, 20));
    }

    private static List<String> names(List<CustomerSummary> summaries) {
        return summaries.stream().map(CustomerSummary::name).toList();
    }
}
