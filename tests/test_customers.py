from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_session
from app.main import app
from app.models import Customer, Order, OrderLine


@pytest.fixture
def client() -> Iterator[TestClient]:
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    TestSession = sessionmaker(engine)
    with TestSession() as session:
        session.add_all(
            [
                Customer(
                    name="Ada Lovelace",
                    email="ada@example.com",
                    orders=[
                        Order(
                            lines=[
                                OrderLine(product="Notebook", quantity=1, unit_price_cents=450),
                                OrderLine(product="Pen", quantity=10, unit_price_cents=120),
                            ]
                        ),
                        Order(lines=[OrderLine(product="Ink", quantity=3, unit_price_cents=200)]),
                    ],
                ),
                Customer(name="Alan Turing", email="alan@example.com"),
                Customer(name="Grace Hopper", email="grace@example.com"),
                Customer(name="100% Cotton Ltd", email="cotton@example.com"),
            ]
        )
        session.commit()

    def override_get_session() -> Iterator[Session]:
        with TestSession() as session:
            yield session

    app.dependency_overrides[get_session] = override_get_session
    # No context manager: the application lifespan would touch the real database.
    yield TestClient(app)
    app.dependency_overrides.clear()
    Base.metadata.drop_all(engine)


def customer_id(client: TestClient, name: str) -> int:
    return client.get("/customers/search", params={"q": name}).json()[0]["id"]


def test_search_matches_name_prefix_ordered_by_name(client: TestClient):
    response = client.get("/customers/search", params={"q": "A"})
    assert response.status_code == 200
    assert [customer["name"] for customer in response.json()] == ["Ada Lovelace", "Alan Turing"]


def test_search_matches_email_prefix(client: TestClient):
    response = client.get("/customers/search", params={"q": "grace@"})
    assert [customer["email"] for customer in response.json()] == ["grace@example.com"]


def test_search_does_not_match_inside_the_value(client: TestClient):
    assert client.get("/customers/search", params={"q": "Lovelace"}).json() == []


def test_search_escapes_like_wildcards(client: TestClient):
    assert client.get("/customers/search", params={"q": "%"}).json() == []
    assert [c["name"] for c in client.get("/customers/search", params={"q": "100%"}).json()] == ["100% Cotton Ltd"]


def test_search_quote_is_not_injected(client: TestClient):
    response = client.get("/customers/search", params={"q": "' UNION SELECT id, name, email FROM customers --"})
    assert response.status_code == 200
    assert response.json() == []


def test_search_applies_limit_and_offset(client: TestClient):
    assert [c["name"] for c in client.get("/customers/search", params={"q": "A", "limit": 1}).json()] == [
        "Ada Lovelace"
    ]
    assert [c["name"] for c in client.get("/customers/search", params={"q": "A", "offset": 1}).json()] == [
        "Alan Turing"
    ]


@pytest.mark.parametrize("params", [{"q": ""}, {"q": "A", "limit": 0}, {"q": "A", "limit": 101}, {"q": "A", "offset": -1}])
def test_search_rejects_out_of_range_parameters(client: TestClient, params: dict):
    assert client.get("/customers/search", params=params).status_code == 422


def test_orders_report_line_count_and_discounted_total(client: TestClient):
    response = client.get(f"/customers/{customer_id(client, 'Ada')}/orders")
    assert response.status_code == 200
    history = response.json()
    assert [(order["line_count"], order["total_cents"]) for order in history] == [(1, 600), (2, 450 + 1140)]


def test_orders_apply_limit_and_offset(client: TestClient):
    ada = customer_id(client, "Ada")
    first = client.get(f"/customers/{ada}/orders", params={"limit": 1}).json()
    second = client.get(f"/customers/{ada}/orders", params={"limit": 1, "offset": 1}).json()
    assert [order["line_count"] for order in first] == [1]
    assert [order["line_count"] for order in second] == [2]


def test_orders_of_customer_without_orders_is_empty(client: TestClient):
    assert client.get(f"/customers/{customer_id(client, 'Grace')}/orders").json() == []


def test_orders_of_unknown_customer_returns_404(client: TestClient):
    response = client.get("/customers/9999/orders")
    assert response.status_code == 404
    assert response.json()["detail"] == "customer not found"


def test_orders_reject_out_of_range_parameters(client: TestClient):
    ada = customer_id(client, "Ada")
    assert client.get(f"/customers/{ada}/orders", params={"limit": 101}).status_code == 422
    assert client.get(f"/customers/{ada}/orders", params={"offset": -1}).status_code == 422
