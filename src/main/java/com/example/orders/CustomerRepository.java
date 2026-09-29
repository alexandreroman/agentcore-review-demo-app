package com.example.orders;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Customers whose name or email contains the given text, ignoring case. Spring Data escapes the LIKE wildcards of
     * the arguments, so user input can only ever match itself.
     */
    List<Customer> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email,
            Pageable pageable);
}
