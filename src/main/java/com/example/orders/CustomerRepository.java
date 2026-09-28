package com.example.orders;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Matches the start of the name, so an index on the name column can serve the query. Email
     * addresses are deliberately not searchable: matching them would turn the search into an oracle
     * that confirms whether a guessed address belongs to a customer.
     */
    List<Customer> findByNameStartingWithIgnoreCase(String namePrefix, Pageable pageable);
}
