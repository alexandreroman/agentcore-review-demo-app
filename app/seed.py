from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Customer, Order, OrderLine

CUSTOMERS = [
    ("Ada Lovelace", "ada@example.com"),
    ("Grace Hopper", "grace@example.com"),
    ("Alan Turing", "alan@example.com"),
]


def seed(session: Session) -> None:
    """Fill an empty database with a few customers and their orders."""
    if session.scalar(select(Customer.id).limit(1)) is not None:
        return
    for count, (name, email) in enumerate(CUSTOMERS, start=1):
        orders = [
            Order(
                lines=[
                    OrderLine(product="Notebook", quantity=count, unit_price_cents=450),
                    OrderLine(product="Pen", quantity=10 * count, unit_price_cents=120),
                ]
            )
            for _ in range(count)
        ]
        session.add(Customer(name=name, email=email, orders=orders))
    session.commit()
