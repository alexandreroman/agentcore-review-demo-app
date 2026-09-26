from dataclasses import dataclass

from app.pricing import line_total, order_total


@dataclass
class Line:
    quantity: int
    unit_price_cents: int


def test_line_below_bulk_quantity_pays_full_price():
    assert line_total(Line(quantity=9, unit_price_cents=100)) == 900


def test_bulk_line_gets_five_percent_off_rounded_down():
    assert line_total(Line(quantity=10, unit_price_cents=100)) == 950
    assert line_total(Line(quantity=10, unit_price_cents=333)) == 3163


def test_order_total_sums_its_lines():
    assert order_total([Line(1, 450), Line(10, 120)]) == 450 + 1140
    assert order_total([]) == 0
