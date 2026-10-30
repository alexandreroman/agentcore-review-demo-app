package com.example.orders;

import java.time.Instant;
import java.util.List;

record OrderSummary(Long id, Instant createdAt, int lineCount, long totalCents) {

    static OrderSummary from(Order order, List<OrderLine> lines) {
        return new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines));
    }
}
