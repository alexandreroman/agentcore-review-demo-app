package com.example.orders;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        var sql = "SELECT id, name, email FROM customers "
                + "WHERE name LIKE :pattern OR email LIKE :pattern "
                + "ORDER BY name LIMIT :limit OFFSET :offset";
        return jdbcClient.sql(sql)
                .param("pattern", "%" + q + "%")
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(CustomerSummary.class)
                .list();
    }

    @GetMapping("/{customerId}/orders")
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
        var linesByOrderId = orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId));
        var history = new ArrayList<OrderSummary>();
        for (var order : orders) {
            var lines = linesByOrderId.getOrDefault(order.getId(), List.<OrderLine>of());
            history.add(new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines)));
        }
        return history;
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
