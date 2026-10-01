package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomerSearchTest {

    private static final CustomerSummary ADA = new CustomerSummary(1L, "Ada Lovelace", "ada@example.com");
    private static final CustomerSummary ALAN = new CustomerSummary(2L, "Alan Turing", "alan@example.com");

    private CustomerController controller;

    @BeforeEach
    void setUp() {
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive between connections.
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:customer-search-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        var jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("CREATE TABLE customers (id BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL, "
                + "email VARCHAR(255) NOT NULL)").update();
        jdbcClient.sql("INSERT INTO customers (id, name, email) VALUES (1, 'Ada Lovelace', 'ada@example.com')")
                .update();
        jdbcClient.sql("INSERT INTO customers (id, name, email) VALUES (2, 'Alan Turing', 'alan@example.com')")
                .update();
        controller = new CustomerController(jdbcClient, null);
    }

    @Test
    void searchMatchesNameOrEmail() {
        assertEquals(List.of(ADA), controller.searchCustomers("Lovelace", 0, 20));
        assertEquals(List.of(ALAN), controller.searchCustomers("alan@example.com", 0, 20));
    }

    @Test
    void searchPagesByName() {
        assertEquals(List.of(ADA), controller.searchCustomers("example.com", 0, 1));
        assertEquals(List.of(ALAN), controller.searchCustomers("example.com", 1, 1));
    }

    @Test
    void injectionAttemptIsTreatedAsLiteralText() {
        assertTrue(controller.searchCustomers("x%' UNION SELECT id, name, email FROM customers --", 0, 20).isEmpty());
    }
}
