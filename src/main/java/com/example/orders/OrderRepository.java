package com.example.orders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"customer", "lines"})
    Optional<Order> findWithCustomerAndLinesById(Long id);

    List<Order> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    List<Order> findWithLinesByIdIn(Collection<Long> ids);
}
