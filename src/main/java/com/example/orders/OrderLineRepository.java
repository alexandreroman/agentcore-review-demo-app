package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderId(Long orderId);

    @Query("select line from OrderLine line join fetch line.order where line.order.id in :orderIds")
    List<OrderLine> findByOrderIdIn(Collection<Long> orderIds);
}
