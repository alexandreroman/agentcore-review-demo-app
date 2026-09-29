package com.example.orders;

/**
 * The id of the owning order plus the fields needed to price one of its lines.
 */
record OrderLineTotal(Long orderId, int quantity, long unitPriceCents) implements PricedLine {

    @Override
    public int getQuantity() {
        return quantity;
    }

    @Override
    public long getUnitPriceCents() {
        return unitPriceCents;
    }
}
