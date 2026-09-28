package com.example.orders;

/**
 * Public view of a customer. It deliberately leaves out the email address, which is personal data
 * that the search endpoint must not hand out.
 */
record CustomerSummary(Long id, String name) {

    static CustomerSummary from(Customer customer) {
        return new CustomerSummary(customer.getId(), customer.getName());
    }
}
