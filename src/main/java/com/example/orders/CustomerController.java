package com.example.orders;

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

    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERM_LENGTH = 100;

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
        var term = q == null ? "" : q.strip();
        if (term.length() < MIN_TERM_LENGTH || term.length() > MAX_TERM_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be between " + MIN_TERM_LENGTH + " and " + MAX_TERM_LENGTH + " characters");
        }
        return customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term,
                pageRequest(page, size, Sort.by("name")));
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var orders = orderRepository.findByCustomerId(customerId, pageRequest(page, size, newestFirst));
        if (orders.isEmpty()) {
            return List.of();
        }
        var orderIds = orders.stream().map(Order::getId).toList();
        var linesByOrderId = orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(line -> line.getOrder().getId()));
        return orders.stream()
                .map(order -> OrderSummary.from(order, linesByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        return PageRequest.of(page, size, sort);
    }
}
