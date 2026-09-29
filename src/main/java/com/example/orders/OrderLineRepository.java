package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    /**
     * Loads the priced lines of several orders in a single query, so a page of orders needs one query for its lines.
     */
    @Query("select new com.example.orders.OrderLineTotal(l.order.id, l.quantity, l.unitPriceCents) "
            + "from OrderLine l where l.order.id in :orderIds")
    List<OrderLineTotal> findLineTotalsByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);
}
