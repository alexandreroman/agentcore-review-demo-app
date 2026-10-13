package com.example.orders;

import java.time.Instant;

record OrderSummary(Long id, Instant createdAt, int lineCount, long totalCents) {

    static OrderSummary from(Order order) {
        var lines = order.getLines();
        return new OrderSummary(order.getId(), order.getCreatedAt(), lines.size(), Pricing.orderTotal(lines));
    }
}
