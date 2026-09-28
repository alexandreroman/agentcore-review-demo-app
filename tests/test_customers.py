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
    TestingSession = sessionmaker(engine)
    with TestingSession() as session:
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
                        )
                    ],
                ),
                Customer(name="Alan Turing", email="alan@example.com"),
                Customer(name="Grace Hopper", email="grace@example.com"),
            ]
        )
        session.commit()

    def override_get_session() -> Iterator[Session]:
        with TestingSession() as session:
            yield session

    app.dependency_overrides[get_session] = override_get_session
    # No context manager: the app lifespan would create and seed the production database.
    yield TestClient(app)
    app.dependency_overrides.clear()
    engine.dispose()


def test_search_matches_name_and_email_prefixes(client: TestClient):
    response = client.get("/customers/search", params={"q": "Ala"})
    assert response.status_code == 200
    assert [row["email"] for row in response.json()] == ["alan@example.com"]

    response = client.get("/customers/search", params={"q": "grace@"})
    assert response.status_code == 200
    assert [row["name"] for row in response.json()] == ["Grace Hopper"]


def test_search_pagination_is_applied(client: TestClient):
    response = client.get("/customers/search", params={"q": "A" * 0 or "a", "limit": 1})
    assert response.status_code == 422  # q shorter than two characters

    response = client.get("/customers/search", params={"q": "al", "limit": 1, "offset": 1})
    assert response.status_code == 200
    assert response.json() == []


def test_search_rejects_out_of_range_pagination(client: TestClient):
    assert client.get("/customers/search", params={"q": "ada", "limit": 0}).status_code == 422
    assert client.get("/customers/search", params={"q": "ada", "limit": 101}).status_code == 422
    assert client.get("/customers/search", params={"q": "ada", "offset": -1}).status_code == 422


def test_search_does_not_treat_wildcards_as_metacharacters(client: TestClient):
    response = client.get("/customers/search", params={"q": "%%"})
    assert response.status_code == 200
    assert response.json() == []


def test_search_does_not_execute_injected_sql(client: TestClient):
    response = client.get("/customers/search", params={"q": "' OR '1'='1"})
    assert response.status_code == 200
    assert response.json() == []


def test_order_history_reports_line_count_and_total(client: TestClient):
    response = client.get("/customers/1/orders")
    assert response.status_code == 200
    history = response.json()
    assert len(history) == 1
    assert history[0]["line_count"] == 2
    assert history[0]["total_cents"] == 450 + 1140


def test_order_history_of_customer_without_orders_is_empty(client: TestClient):
    response = client.get("/customers/2/orders")
    assert response.status_code == 200
    assert response.json() == []


def test_order_history_of_unknown_customer_is_404(client: TestClient):
    response = client.get("/customers/999/orders")
    assert response.status_code == 404
    assert response.json()["detail"] == "customer not found"


def test_order_history_rejects_out_of_range_pagination(client: TestClient):
    assert client.get("/customers/1/orders", params={"limit": 0}).status_code == 422
    assert client.get("/customers/1/orders", params={"limit": 101}).status_code == 422
    assert client.get("/customers/1/orders", params={"offset": -1}).status_code == 422
