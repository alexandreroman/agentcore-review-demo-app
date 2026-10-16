package com.example.orders;

record CustomerSummary(Long id, String name, String email) {

    static CustomerSummary from(Customer customer) {
        return new CustomerSummary(customer.getId(), customer.getName(), customer.getEmail());
    }
}
