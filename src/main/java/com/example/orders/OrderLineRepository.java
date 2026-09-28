package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    /**
     * The owning order is fetched along with the lines, so callers can read {@code getOrderId()}
     * after the persistence context is closed.
     */
    @EntityGraph(attributePaths = "order")
    List<OrderLine> findByOrderIdIn(Collection<Long> orderIds);
}
