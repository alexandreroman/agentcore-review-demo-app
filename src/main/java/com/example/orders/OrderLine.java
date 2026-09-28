package com.example.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_lines", indexes = @Index(columnList = "order_id"))
class OrderLine implements PricedLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * Read-only mapping of the {@code order_id} foreign key column. The association above owns the
     * column, so this field is never written; it is loaded with the row and lets callers read the
     * owning order id without going through the lazy association.
     */
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false)
    private String product;

    private int quantity;

    private long unitPriceCents;

    protected OrderLine() {
    }

    OrderLine(String product, int quantity, long unitPriceCents) {
        this.product = product;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    Long getId() {
        return id;
    }

    /**
     * Id of the owning order, read from the foreign key column of this row, so it never initializes
     * the lazy association. It is only populated for lines loaded from the database.
     */
    Long getOrderId() {
        return orderId;
    }

    String getProduct() {
        return product;
    }

    @Override
    public int getQuantity() {
        return quantity;
    }

    @Override
    public long getUnitPriceCents() {
        return unitPriceCents;
    }
}
