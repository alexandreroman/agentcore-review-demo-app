package com.example.orders;

/**
 * Priced line together with the id of its order, so the lines of several orders can be fetched in one query.
 */
interface OrderLineView extends PricedLine {

    Long getOrderId();
}
