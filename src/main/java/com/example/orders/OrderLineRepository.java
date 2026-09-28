package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    /**
     * The owning order is not fetched: {@code getOrderId()} reads the foreign key that comes with
     * the {@code order_lines} row, so callers can group by it after the persistence context is
     * closed.
     */
    List<OrderLine> findByOrderIdIn(Collection<Long> orderIds);
}
