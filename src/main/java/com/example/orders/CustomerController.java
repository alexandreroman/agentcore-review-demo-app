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

    private static final int MIN_SEARCH_TERM_LENGTH = 2;
    private static final int MAX_SEARCH_TERM_LENGTH = 100;
    private static final int MAX_SEARCH_RESULT_WINDOW = 1000;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;

    CustomerController(CustomerRepository customerRepository, OrderRepository orderRepository,
            OrderLineRepository orderLineRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
    }

    /**
     * Looks customers up by the start of their name or by their full email address. The summaries
     * never contain the email address, and an email only matches when it is spelled out completely,
     * so the endpoint cannot be used to harvest addresses.
     */
    @GetMapping("/search")
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var byName = pageRequest(page, size, Sort.by(Sort.Direction.ASC, "name", "id"));
        var term = q.strip();
        if (term.length() < MIN_SEARCH_TERM_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be at least " + MIN_SEARCH_TERM_LENGTH + " characters");
        }
        if (term.length() > MAX_SEARCH_TERM_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be at most " + MAX_SEARCH_TERM_LENGTH + " characters");
        }
        if ((long) page * size >= MAX_SEARCH_RESULT_WINDOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page * size must be below " + MAX_SEARCH_RESULT_WINDOW + "; refine q instead");
        }
        return customerRepository.findByNameStartingWithIgnoreCaseOrEmailIgnoreCase(term, term, byName).stream()
                .map(CustomerSummary::from)
                .toList();
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var newestFirst = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var orders = orderRepository.findByCustomerId(customerId, newestFirst);
        if (orders.isEmpty()) {
            return List.of();
        }
        var orderIds = orders.stream().map(Order::getId).toList();
        var linesByOrder = orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId));
        return orders.stream()
                .map(order -> OrderSummary.from(order, linesByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        return PageRequest.of(page, size, sort);
    }
}
