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
     * Read-only mapping of the foreign key that is already part of every {@code order_lines} row, so
     * the owning order's id can be read without fetching the order. The association stays the only
     * writable side, and {@link #setOrder(Order)} keeps this field in sync with it.
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
        this.orderId = order == null ? null : order.getId();
    }

    Order getOrder() {
        return order;
    }

    Long getId() {
        return id;
    }

    /**
     * Id of the owning order. Loaded lines read it straight from the foreign key column; for a line
     * that was just attached to a not yet persisted order it falls back to the association, so it
     * cannot drift from it. It is {@code null} for a line that has no order yet.
     */
    Long getOrderId() {
        if (orderId != null) {
            return orderId;
        }
        return order == null ? null : order.getId();
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
