package com.example.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    private long customerIdOf(String name) {
        return customerRepository.findAll().stream()
                .filter(customer -> name.equals(customer.getName()))
                .map(Customer::getId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void searchMatchesNameAndEmail() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "Ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$[0].email").value("ada@example.com"));

        mockMvc.perform(get("/customers/search").param("q", "grace@"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Grace Hopper"));
    }

    @Test
    void searchIsPagedAndOrderedByName() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "a").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alan Turing"));
    }

    @Test
    void searchTreatsTheTermAsDataOnly() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchRejectsInvalidPagingAndBlankTerm() throws Exception {
        mockMvc.perform(get("/customers/search").param("q", "a").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/search").param("q", "a").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/search").param("q", "a").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/search").param("q", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderHistoryCountsLinesAndSumsTotals() throws Exception {
        var graceId = customerIdOf("Grace Hopper");

        // Each of Grace's two orders holds 2 notebooks at 450 and 20 pens at 120 (bulk discounted).
        var expectedTotal = 2 * 450 + 20 * 120 * 95 / 100;
        mockMvc.perform(get("/customers/{customerId}/orders", graceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lineCount").value(2))
                .andExpect(jsonPath("$[0].totalCents").value(expectedTotal))
                .andExpect(jsonPath("$[1].lineCount").value(2))
                .andExpect(jsonPath("$[1].totalCents").value(expectedTotal));
    }

    @Test
    void orderHistoryIsPagedAndRejectsInvalidPaging() throws Exception {
        var graceId = customerIdOf("Grace Hopper");

        mockMvc.perform(get("/customers/{customerId}/orders", graceId).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/customers/{customerId}/orders", graceId).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/customers/{customerId}/orders", graceId).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
