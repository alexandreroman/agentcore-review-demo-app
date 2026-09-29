package com.example.orders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    CustomerController(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/search")
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var byName = pageRequest(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, byName).stream()
                .map(customer -> new CustomerSummary(customer.getId(), customer.getName(), customer.getEmail()))
                .toList();
    }

    @GetMapping("/{customerId}/orders")
    @Transactional(readOnly = true)
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var orders = orderRepository.findByCustomerId(customerId, pageRequest(page, size, newestFirst));
        if (orders.isEmpty()) {
            return List.of();
        }
        var linesByOrderId = linesByOrderId(orders);
        var history = new ArrayList<OrderSummary>();
        for (var order : orders) {
            var lines = linesByOrderId.getOrDefault(order.getId(), List.of());
            history.add(new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines)));
        }
        return history;
    }

    /**
     * Loads the lines of the whole page with a single query, so the history does not issue one query per order.
     */
    private Map<Long, List<OrderLine>> linesByOrderId(List<Order> orders) {
        var orderIds = orders.stream()
                .map(Order::getId)
                .toList();
        return orderRepository.findWithLinesByIdIn(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Order::getLines));
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        return PageRequest.of(page, size, sort);
    }
}
