from collections.abc import Iterator
from datetime import datetime

import pytest
from fastapi import FastAPI, HTTPException
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from app.database import Base
from app.models import Customer, Order, OrderLine
from app.routers.customers import customer_orders, router, search_customers

OLD_ORDER_AT = datetime(2024, 1, 1, 9, 0)
NEW_ORDER_AT = datetime(2024, 3, 1, 9, 0)


@pytest.fixture
def session() -> Iterator[Session]:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        session.add_all(
            [
                Customer(
                    name="Ada Lovelace",
                    email="ada@example.com",
                    orders=[
                        Order(
                            created_at=OLD_ORDER_AT,
                            lines=[OrderLine(product="Notebook", quantity=1, unit_price_cents=450)],
                        ),
                        Order(
                            created_at=NEW_ORDER_AT,
                            lines=[
                                OrderLine(product="Pen", quantity=10, unit_price_cents=120),
                                OrderLine(product="Ink", quantity=2, unit_price_cents=300),
                            ],
                        ),
                    ],
                ),
                # The literal percent sign checks that LIKE wildcards are escaped.
                Customer(name="Bill 50%Off", email="bill@example.com"),
                Customer(name="Grace Hopper", email="grace@example.com"),
            ]
        )
        session.commit()
        yield session


def names(customers) -> list[str]:
    return [customer.name for customer in customers]


def test_search_matches_name(session: Session):
    assert names(search_customers(session, "Lovelace")) == ["Ada Lovelace"]


def test_search_matches_email(session: Session):
    assert names(search_customers(session, "grace@")) == ["Grace Hopper"]


def test_search_orders_by_name_and_pages(session: Session):
    assert names(search_customers(session, "example.com")) == ["Ada Lovelace", "Bill 50%Off", "Grace Hopper"]
    assert names(search_customers(session, "example.com", limit=1)) == ["Ada Lovelace"]
    assert names(search_customers(session, "example.com", limit=1, offset=1)) == ["Bill 50%Off"]
    assert search_customers(session, "example.com", offset=3) == []


def test_search_escapes_like_wildcards(session: Session):
    # Unescaped, these patterns would match (and export) every customer.
    assert search_customers(session, "%%") == []
    assert search_customers(session, "__") == []
    # A percent sign is matched literally.
    assert names(search_customers(session, "%Off")) == ["Bill 50%Off"]


def test_order_history_summarises_lines_newest_first(session: Session):
    customer = session.scalars(select(Customer).where(Customer.name == "Ada Lovelace")).one()
    orders = customer_orders(session, customer.id)
    assert [order.created_at for order in orders] == [NEW_ORDER_AT, OLD_ORDER_AT]
    assert [order.line_count for order in orders] == [2, 1]
    # The 10 pens get the 5% bulk discount: 1200 -> 1140, plus 2 * 300 for the ink.
    assert [order.total_cents for order in orders] == [1140 + 600, 450]


def test_order_history_of_unknown_customer_is_404(session: Session):
    with pytest.raises(HTTPException) as excinfo:
        customer_orders(session, 999999)
    assert excinfo.value.status_code == 404


def parameters(path: str) -> dict[str, dict]:
    app = FastAPI()
    app.include_router(router)
    schema = app.openapi()
    return {param["name"]: param for param in schema["paths"][path]["get"]["parameters"]}


def test_search_parameters_declare_their_bounds():
    params = parameters("/customers/search")
    assert params["q"]["schema"]["minLength"] == 2
    assert params["q"]["schema"]["maxLength"] == 100
    assert params["limit"]["schema"]["minimum"] == 1
    assert params["limit"]["schema"]["maximum"] == 100
    assert params["offset"]["schema"]["minimum"] == 0


def test_order_history_parameters_declare_their_bounds():
    params = parameters("/customers/{customer_id}/orders")
    assert params["limit"]["schema"]["minimum"] == 1
    assert params["limit"]["schema"]["maximum"] == 100
    assert params["offset"]["schema"]["minimum"] == 0
