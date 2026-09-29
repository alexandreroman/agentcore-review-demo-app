package com.example.orders;

import java.util.List;

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

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final Sort BY_NAME = Sort.by("name");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    CustomerController(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/search")
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var term = searchTerm(q);
        var pageRequest = pageRequest(page, size, BY_NAME);
        return customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term, pageRequest)
                .stream()
                .map(CustomerSummary::from)
                .toList();
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var orderIds = orderRepository.findByCustomerId(customerId, pageRequest(page, size, NEWEST_FIRST)).stream()
                .map(Order::getId)
                .toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findWithLinesByIdIn(orderIds, NEWEST_FIRST).stream()
                .map(OrderSummary::from)
                .toList();
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        return PageRequest.of(page, size, sort);
    }

    private static String searchTerm(String q) {
        var term = q.strip();
        if (term.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must not be blank");
        }
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return term;
    }
}
