package com.example.orders;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/customers")
class CustomerController {

    private static final int MIN_SEARCH_TERM_LENGTH = 2;
    private static final int MAX_SEARCH_RESULT_WINDOW = 1000;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;

    CustomerController(CustomerRepository customerRepository, OrderRepository orderRepository,
            OrderLineRepository orderLineRepository) {
        this.customerRepository = customerRepository;
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
        var term = q == null ? "" : q.strip();
        if (term.length() < MIN_SEARCH_TERM_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be at least " + MIN_SEARCH_TERM_LENGTH + " characters");
        }
        if ((long) page * size >= MAX_SEARCH_RESULT_WINDOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page * size must be below " + MAX_SEARCH_RESULT_WINDOW + "; refine q instead");
        }
        var byName = Sort.by(Sort.Direction.ASC, "name", "id");
        var customers = customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term,
                PageRequest.of(page, size, byName));
        var summaries = new ArrayList<CustomerSummary>();
        for (var customer : customers) {
            summaries.add(new CustomerSummary(customer.getId(), customer.getName(), customer.getEmail()));
        }
        return summaries;
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
        if (orders.isEmpty()) {
            return List.of();
        }
        var orderIds = orders.stream().map(Order::getId).toList();
        var linesByOrder = orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId));
        var history = new ArrayList<OrderSummary>();
        for (var order : orders) {
            var lines = linesByOrder.getOrDefault(order.getId(), List.of());
            history.add(new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines)));
        }
        return history;
    }
}