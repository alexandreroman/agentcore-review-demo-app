package com.example.orders;

/**
 * The pricing-relevant fields of an order line together with the id of its order, so the lines of several orders can be
 * fetched in one query and grouped per order.
 */
record PricedOrderLine(Long orderId, int quantity, long unitPriceCents) implements PricedLine {

    @Override
    public int getQuantity() {
        return quantity;
    }

    @Override
    public long getUnitPriceCents() {
        return unitPriceCents;
    }
}
