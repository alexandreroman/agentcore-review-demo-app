package com.example.orders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    CustomerController(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var byName = Sort.by(Sort.Direction.ASC, "name");
        var customers = customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q,
                pageRequest(page, size, byName));
        var summaries = new ArrayList<CustomerSummary>();
        for (var customer : customers) {
            summaries.add(new CustomerSummary(customer.getId(), customer.getName(), customer.getEmail()));
        }
        return summaries;
    }

    @GetMapping("/{customerId}/orders")
    @Transactional(readOnly = true)
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var request = pageRequest(page, size, newestFirst);
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        var orders = orderRepository.findByCustomerId(customerId, request);
        if (orders.isEmpty()) {
            return List.of();
        }
        var linesByOrderId = linesByOrderId(orders.stream().map(Order::getId).toList());
        var history = new ArrayList<OrderSummary>();
        for (var order : orders) {
            var lines = linesByOrderId.getOrDefault(order.getId(), List.of());
            history.add(new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines)));
        }
        return history;
    }

    /**
     * Loads the lines of all given orders with a single query, to avoid one query per order.
     */
    private Map<Long, List<OrderLine>> linesByOrderId(Collection<Long> orderIds) {
        var linesByOrderId = new HashMap<Long, List<OrderLine>>();
        for (var order : orderRepository.findWithLinesByIdIn(orderIds)) {
            linesByOrderId.put(order.getId(), order.getLines());
        }
        return linesByOrderId;
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
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
