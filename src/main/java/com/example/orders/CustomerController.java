package com.example.orders;

import java.util.List;
import java.util.Map;
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

    private static final int MAX_QUERY_LENGTH = 100;
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

    @GetMapping("/search")
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var byName = pageRequest(page, size, Sort.by("name"));
        if (q.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "q must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return customerRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, byName)
                .stream()
                .map(customer -> new CustomerSummary(customer.getId(), customer.getName(), customer.getEmail()))
                .toList();
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var newestFirst = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var orders = orderRepository.findByCustomerId(customerId, newestFirst);
        var linesByOrderId = linesByOrderId(orders);
        return orders.stream()
                .map(order -> {
                    List<PricedOrderLine> lines = linesByOrderId.getOrDefault(order.getId(), List.of());
                    return new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(),
                            Pricing.orderTotal(lines));
                })
                .toList();
    }

    /**
     * The lines of all given orders, grouped by order id, fetched in one query instead of one query per order.
     */
    private Map<Long, List<PricedOrderLine>> linesByOrderId(List<Order> orders) {
        var orderIds = orders.stream().map(Order::getId).toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderLineRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(PricedOrderLine::orderId));
    }

    /**
     * Validates the paging parameters shared by the endpoints of this controller.
     */
    private static PageRequest pageRequest(int page, int size, Sort sort) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        return PageRequest.of(page, size, sort);
    }
}
