package com.example.orders;

import java.util.ArrayList;
import java.util.List;

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
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        var sql = "SELECT id, name, email FROM customers "
                + "WHERE name LIKE :pattern ESCAPE '!' OR email LIKE :pattern ESCAPE '!' "
                + "ORDER BY name LIMIT :limit OFFSET :offset";
        return jdbcClient.sql(sql)
                .param("pattern", "%" + escapeLike(q) + "%")
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(CustomerSummary.class)
                .list();
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var orders = orderRepository.findByCustomerId(customerId, PageRequest.of(page, size, newestFirst));
        var history = new ArrayList<OrderSummary>();
        for (var order : orders) {
            var lines = orderLineRepository.findByOrderId(order.getId());
            history.add(new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines)));
        }
        return history;
    }
}
