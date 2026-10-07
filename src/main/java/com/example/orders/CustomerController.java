package com.example.orders;

import java.util.ArrayList;
import java.util.List;

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
    @Transactional(readOnly = true)
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        var byName = Sort.by(Sort.Direction.ASC, "name");
        var customers = customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q,
                PageRequest.of(page, size, byName));
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
