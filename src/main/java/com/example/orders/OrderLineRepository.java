package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    /**
     * The priced lines of several orders in a single query, so a page of orders does not need one query per order.
     */
    @Query("select new com.example.orders.PricedOrderLine(l.order.id, l.quantity, l.unitPriceCents) "
            + "from OrderLine l where l.order.id in :orderIds")
    List<PricedOrderLine> findByOrderIdIn(Collection<Long> orderIds);
}
