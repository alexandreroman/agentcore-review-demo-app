package com.example.orders;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
class CustomerController {

    private static final String SEARCH_SQL = "SELECT id, name, email FROM customers "
            + "WHERE name LIKE :term OR email LIKE :term "
            + "ORDER BY name LIMIT :limit OFFSET :offset";

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
    List<CustomerSummary> searchCustomers(@RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return jdbcClient.sql(SEARCH_SQL)
                .param("term", "%" + q + "%")
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(CustomerSummary.class)
                .list();
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var orders = orderRepository.findByCustomerId(customerId, PageRequest.of(page, size, newestFirst));
        if (orders.isEmpty()) {
            return List.of();
        }
        var orderIds = orders.stream()
                .map(Order::getId)
                .toList();
        var linesByOrderId = orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(line -> line.getOrder().getId()));
        var history = new ArrayList<OrderSummary>();
        for (var order : orders) {
            var lines = linesByOrderId.getOrDefault(order.getId(), List.of());
            history.add(new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines)));
        }
        return history;
    }
}
