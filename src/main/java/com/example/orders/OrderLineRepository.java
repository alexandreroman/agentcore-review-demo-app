package com.example.orders;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    @Query("select l.order.id as orderId, l.quantity as quantity, l.unitPriceCents as unitPriceCents "
            + "from OrderLine l where l.order.id in :orderIds")
    List<OrderLineView> findViewsByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);
}
