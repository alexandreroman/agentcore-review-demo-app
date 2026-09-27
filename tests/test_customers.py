from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_session
from app.main import app
from app.models import Customer, Order, OrderLine

ADA_ID = 1
GRACE_ID = 2
ALAN_ID = 3
MISSING_ID = 999


def fill(session: Session) -> None:
    session.add_all(
        [
            Customer(
                id=ADA_ID,
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
            Customer(
                id=GRACE_ID,
                name="Grace Hopper",
                email="grace@example.com",
                orders=[
                    Order(lines=[OrderLine(product="Notebook", quantity=2, unit_price_cents=450)]),
                    Order(lines=[OrderLine(product="Pen", quantity=20, unit_price_cents=120)]),
                ],
            ),
            Customer(id=ALAN_ID, name="Alan Turing", email="alan@example.com"),
        ]
    )
    session.commit()


@pytest.fixture(name="client")
def client_fixture() -> Iterator[TestClient]:
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    TestSession = sessionmaker(engine)
    with TestSession() as session:
        fill(session)

    def session_override() -> Iterator[Session]:
        with TestSession() as session:
            yield session

    app.dependency_overrides[get_session] = session_override
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_search_matches_a_name_prefix(client: TestClient):
    response = client.get("/customers/search", params={"q": "grace h"})
    assert response.status_code == 200
    assert [customer["email"] for customer in response.json()] == ["grace@example.com"]


def test_search_matches_an_email_prefix(client: TestClient):
    response = client.get("/customers/search", params={"q": "alan@"})
    assert response.status_code == 200
    assert [customer["name"] for customer in response.json()] == ["Alan Turing"]


def test_search_paginates_by_name(client: TestClient):
    names = [
        client.get("/customers/search", params={"q": "a", "limit": 1, "offset": offset}).json()
        for offset in (0, 1, 2)
    ]
    assert [page and page[0]["name"] for page in names] == ["Ada Lovelace", "Alan Turing", None]


def test_search_without_match_returns_an_empty_list(client: TestClient):
    assert client.get("/customers/search", params={"q": "nobody"}).json() == []


def test_search_takes_like_wildcards_literally(client: TestClient):
    assert client.get("/customers/search", params={"q": "%"}).json() == []
    assert client.get("/customers/search", params={"q": "_da"}).json() == []


def test_search_rejects_a_quote_injection_as_plain_text(client: TestClient):
    response = client.get("/customers/search", params={"q": "' UNION SELECT id, name, email FROM customers --"})
    assert response.status_code == 200
    assert response.json() == []


@pytest.mark.parametrize("params", [{"q": ""}, {"q": "a", "limit": 0}, {"q": "a", "limit": 101}, {"q": "a", "offset": -1}])
def test_search_rejects_out_of_bounds_parameters(client: TestClient, params: dict):
    assert client.get("/customers/search", params=params).status_code == 422


def test_order_history_reports_line_counts_and_totals(client: TestClient):
    response = client.get(f"/customers/{ADA_ID}/orders")
    assert response.status_code == 200
    assert [(order["line_count"], order["total_cents"]) for order in response.json()] == [(2, 450 + 1140)]


def test_order_history_lists_the_newest_order_first(client: TestClient):
    orders = client.get(f"/customers/{GRACE_ID}/orders").json()
    assert [order["total_cents"] for order in orders] == [2280, 900]


def test_order_history_of_a_customer_without_orders_is_empty(client: TestClient):
    assert client.get(f"/customers/{ALAN_ID}/orders").json() == []


def test_order_history_paginates(client: TestClient):
    orders = client.get(f"/customers/{GRACE_ID}/orders", params={"limit": 1, "offset": 1}).json()
    assert [order["total_cents"] for order in orders] == [900]


def test_order_history_of_an_unknown_customer_is_404(client: TestClient):
    response = client.get(f"/customers/{MISSING_ID}/orders")
    assert response.status_code == 404
    assert response.json()["detail"] == "customer not found"


@pytest.mark.parametrize("params", [{"limit": 0}, {"limit": 101}, {"offset": -1}])
def test_order_history_rejects_out_of_bounds_pagination(client: TestClient, params: dict):
    assert client.get(f"/customers/{ADA_ID}/orders", params=params).status_code == 422
