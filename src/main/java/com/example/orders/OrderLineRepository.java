package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order"})
    List<OrderLine> findByOrderIdIn(Collection<Long> orderIds);
}
