package com.example.orders;

import java.util.List;
import java.util.Map;
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
    private static final int MAX_SEARCH_TERM_LENGTH = 100;

    private static final String SEARCH_SQL = """
            SELECT id, name, email FROM customers
            WHERE name LIKE :pattern OR email LIKE :pattern
            ORDER BY name
            OFFSET :offset ROWS FETCH FIRST :size ROWS ONLY
            """;

    private final JdbcClient jdbcClient;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;

    CustomerController(JdbcClient jdbcClient, OrderRepository orderRepository,
            OrderLineRepository orderLineRepository) {
        this.jdbcClient = jdbcClient;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        var term = q.strip();
        if (term.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must not be blank");
        }
        if (term.length() > MAX_SEARCH_TERM_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be at most " + MAX_SEARCH_TERM_LENGTH + " characters");
        }
        return jdbcClient.sql(SEARCH_SQL)
                .param("pattern", "%" + term + "%")
                .param("offset", (long) page * size)
                .param("size", size)
                .query(CustomerSummary.class)
                .list();
    }

    @GetMapping("/{customerId}/orders")
    @Transactional(readOnly = true)
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var orders = orderRepository.findByCustomerId(customerId, PageRequest.of(page, size, newestFirst));
        if (orders.isEmpty()) {
            return List.of();
        }
        var orderIds = orders.stream()
                .map(Order::getId)
                .toList();
        Map<Long, List<OrderLine>> linesByOrderId = orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(line -> line.getOrder().getId()));
        return orders.stream()
                .map(order -> OrderSummary.from(order, linesByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    private static void validatePaging(int page, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
    }
}
