"""Order pricing rules, in cents."""

from collections.abc import Iterable
from typing import Protocol

BULK_QUANTITY = 10
BULK_DISCOUNT_PERCENT = 5


class PricedLine(Protocol):
    quantity: int
    unit_price_cents: int


def line_total(line: PricedLine) -> int:
    """Price of one line; 10 units or more get a 5% discount, rounded down to the cent."""
    gross = line.quantity * line.unit_price_cents
    if line.quantity >= BULK_QUANTITY:
        return gross * (100 - BULK_DISCOUNT_PERCENT) // 100
    return gross


def order_total(lines: Iterable[PricedLine]) -> int:
    return sum(line_total(line) for line in lines)
