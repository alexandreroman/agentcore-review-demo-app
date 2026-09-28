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

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/customers")
class CustomerController {

    private static final int MIN_SEARCH_TERM_LENGTH = 3;
    private static final int MAX_SEARCH_TERM_LENGTH = 100;
    private static final int MAX_SEARCH_RESULT_WINDOW = 1000;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final CustomerSearchRateLimiter searchRateLimiter;

    CustomerController(CustomerRepository customerRepository, OrderRepository orderRepository,
            OrderLineRepository orderLineRepository, CustomerSearchRateLimiter searchRateLimiter) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.searchRateLimiter = searchRateLimiter;
    }

    /**
     * Looks customers up by the start of their name. Email addresses are neither returned nor
     * matched, so the endpoint cannot confirm that an address belongs to a customer. The minimum
     * term length, the result window and the per-caller rate limit make sweeping the customer list
     * impractical, but they are not a substitute for authentication: this service has none, so it
     * has to run behind an authenticating gateway.
     */
    @GetMapping("/search")
    List<CustomerSummary> searchCustomers(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        if (!searchRateLimiter.tryAcquire(callerKey(request))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "too many searches; at most " + CustomerSearchRateLimiter.MAX_SEARCHES_PER_WINDOW
                            + " are allowed per " + CustomerSearchRateLimiter.WINDOW.toMinutes() + " minute(s)");
        }
        var byNamePage = validatedPageRequest(page, size, Sort.by(Sort.Direction.ASC, "name", "id"));
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
        return customerRepository.findByNameStartingWithIgnoreCase(term, byNamePage).stream()
                .map(CustomerSummary::from)
                .toList();
    }

    @GetMapping("/{customerId}/orders")
    List<OrderSummary> customerOrders(@PathVariable long customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var newestFirstPage = validatedPageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var orders = orderRepository.findByCustomerId(customerId, newestFirstPage);
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

    /**
     * Identifies the caller by its remote address; forwarded headers are not trusted because clients
     * can spoof them.
     */
    private static String callerKey(HttpServletRequest request) {
        var remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    /**
     * Validates the paging parameters, rejecting them with a 400 before building the page request.
     */
    private PageRequest validatedPageRequest(int page, int size, Sort sort) {
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
