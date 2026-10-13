package com.example.orders;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/customers")
class CustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String SEARCH_SQL = "SELECT id, name, email FROM customers "
            + "WHERE name LIKE :pattern OR email LIKE :pattern "
            + "ORDER BY name LIMIT :limit OFFSET :offset";

    private final JdbcClient jdbcClient;
    private final OrderRepository orderRepository;

    CustomerController(JdbcClient jdbcClient, OrderRepository orderRepository) {
        this.jdbcClient = jdbcClient;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkPaging(page, size);
        return jdbcClient.sql(SEARCH_SQL)
                .param("pattern", q + "%")
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(CustomerSummary.class)
                .list();
    }

    @GetMapping("/{customerId}/orders")
    @Transactional(readOnly = true)
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkPaging(page, size);
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var orders = orderRepository.findByCustomerId(customerId, PageRequest.of(page, size, newestFirst));
        if (orders.isEmpty()) {
            return List.of();
        }
        var ids = orders.stream()
                .map(Order::getId)
                .toList();
        var withLines = orderRepository.findWithLinesByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity(), (first, second) -> first));
        return orders.stream()
                .map(order -> OrderSummary.from(withLines.get(order.getId())))
                .toList();
    }

    private static void checkPaging(int page, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
    }
}
