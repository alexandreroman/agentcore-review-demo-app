package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class OrderLineRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderLineRepository orderLineRepository;

    @Test
    void findByOrderIdInReadsTheOrderIdWithoutFetchingTheOrder() {
        var customer = entityManager.persist(new Customer("Ada Lovelace", "ada@example.com"));
        var first = persistedOrder(customer, "Notebook", "Pen");
        var second = persistedOrder(customer, "Eraser");
        persistedOrder(customer, "Ruler");
        entityManager.flush();
        entityManager.clear();

        var lines = orderLineRepository.findByOrderIdIn(List.of(first.getId(), second.getId()));

        assertEquals(3, lines.size());
        assertFalse(lines.stream().anyMatch(line -> Hibernate.isInitialized(line.getOrder())),
                "the owning order must not be fetched");

        // Detaching the lines makes the association unusable, like a persistence context that was
        // closed before the controller groups the lines by their order id.
        lines.forEach(entityManager::detach);
        var linesByOrder = lines.stream().collect(Collectors.groupingBy(OrderLine::getOrderId));

        assertEquals(Set.of(first.getId(), second.getId()), linesByOrder.keySet());
        assertEquals(List.of("Notebook", "Pen"), products(linesByOrder.get(first.getId())));
        assertEquals(List.of("Eraser"), products(linesByOrder.get(second.getId())));
    }

    @Test
    void orderIdOfALineOfAFreshlyPersistedOrderFallsBackToTheAssociation() {
        var customer = entityManager.persist(new Customer("Grace Hopper", "grace@example.com"));
        var order = persistedOrder(customer, "Notebook");
        entityManager.flush();

        assertEquals(order.getId(), order.getLines().get(0).getOrderId());
    }

    private Order persistedOrder(Customer customer, String... products) {
        var order = new Order(customer);
        for (var product : products) {
            order.addLine(new OrderLine(product, 1, 100));
        }
        return entityManager.persist(order);
    }

    private static List<String> products(List<OrderLine> lines) {
        return lines.stream().map(OrderLine::getProduct).sorted().toList();
    }
}
