package com.example.orders;

import java.time.Instant;

record OrderSummary(Long id, Instant createdAt, int lineCount, long totalCents) {
}
