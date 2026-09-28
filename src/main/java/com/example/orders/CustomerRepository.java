package com.example.orders;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Matches the start of the name, so an index on the name column can serve the query, or the
     * complete email address, so addresses cannot be discovered with partial matches.
     */
    List<Customer> findByNameStartingWithIgnoreCaseOrEmailIgnoreCase(String namePrefix, String email,
            Pageable pageable);
}
