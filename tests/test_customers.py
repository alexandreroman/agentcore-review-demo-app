from datetime import datetime

import pytest
from fastapi import HTTPException
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.database import Base
from app.main import app
from app.models import Customer, Order, OrderLine
from app.routers.customers import customer_orders, search_customers


@pytest.fixture
def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        yield session


@pytest.fixture
def customers(session):
    ada = Customer(name="Ada Lovelace", email="ada@example.com")
    grace = Customer(name="Grace Hopper", email="grace@example.com")
    session.add_all([ada, grace])
    session.commit()
    return ada, grace


def add_order(session, customer, created_at, lines):
    order = Order(
        customer_id=customer.id,
        created_at=created_at,
        lines=[OrderLine(product=product, quantity=quantity, unit_price_cents=price) for product, quantity, price in lines],
    )
    session.add(order)
    session.commit()
    return order


def query_schema(path: str, name: str) -> dict:
    parameters = app.openapi()["paths"][path]["get"]["parameters"]
    return next(parameter for parameter in parameters if parameter["name"] == name)["schema"]


def test_search_matches_a_name_prefix(session, customers):
    found = search_customers(session, q="Ada")

    assert [customer.name for customer in found] == ["Ada Lovelace"]


def test_search_matches_an_email_prefix(session, customers):
    found = search_customers(session, q="grace@")

    assert [customer.name for customer in found] == ["Grace Hopper"]


def test_search_masks_the_email(session, customers):
    found = search_customers(session, q="Ada")

    assert found[0].email == "a**@example.com"


def test_search_treats_wildcards_and_quotes_as_plain_text(session, customers):
    assert search_customers(session, q="%a%") == []
    assert search_customers(session, q="_da") == []
    assert search_customers(session, q="' OR '1'='1") == []


def test_search_orders_by_name_and_paginates(session, customers):
    assert [customer.name for customer in search_customers(session, q="e@example.com")] == []
    page = search_customers(session, q="a" * 3, limit=1, offset=0)
    assert page == []

    all_names = [customer.name for customer in search_customers(session, q="Ada", limit=1, offset=0)]
    assert all_names == ["Ada Lovelace"]
    assert search_customers(session, q="Ada", limit=1, offset=1) == []


@pytest.mark.parametrize("path", ["/customers/search", "/customers/{customer_id}/orders"])
def test_pagination_bounds_are_declared(path):
    assert query_schema(path, "limit")["minimum"] == 1
    assert query_schema(path, "limit")["maximum"] == 100
    assert query_schema(path, "offset")["minimum"] == 0


def test_search_term_has_a_minimum_length():
    assert query_schema("/customers/search", "q")["minLength"] == 3
    assert query_schema("/customers/search", "q")["maxLength"] == 100


def test_order_history_summarises_every_order(session, customers):
    ada, _ = customers
    add_order(session, ada, datetime(2024, 1, 1), [("Notebook", 1, 450), ("Pen", 10, 120)])

    history = customer_orders(session, customer_id=ada.id)

    assert len(history) == 1
    assert history[0].line_count == 2
    assert history[0].total_cents == 450 + 1140


def test_order_history_is_newest_first(session, customers):
    ada, _ = customers
    older = add_order(session, ada, datetime(2024, 1, 1), [("Pen", 1, 120)])
    newer = add_order(session, ada, datetime(2024, 3, 1), [("Pen", 1, 120)])
    same_day = add_order(session, ada, datetime(2024, 3, 1), [("Pen", 1, 120)])

    history = customer_orders(session, customer_id=ada.id)

    assert [summary.id for summary in history] == [same_day.id, newer.id, older.id]
    assert [summary.id for summary in customer_orders(session, customer_id=ada.id, limit=1, offset=1)] == [newer.id]


def test_order_history_ignores_other_customers(session, customers):
    ada, grace = customers
    add_order(session, grace, datetime(2024, 1, 1), [("Pen", 1, 120)])

    assert customer_orders(session, customer_id=ada.id) == []


def test_order_history_of_an_unknown_customer_is_404(session, customers):
    with pytest.raises(HTTPException) as excinfo:
        customer_orders(session, customer_id=999)

    assert excinfo.value.status_code == 404
    assert excinfo.value.detail == "customer not found"
