from typing import Annotated

from fastapi import APIRouter, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.database import SessionDep
from app.models import Customer, Order
from app.pricing import order_total
from app.schemas import CustomerOut, OrderSummary

router = APIRouter(prefix="/customers", tags=["customers"])

LimitDep = Annotated[int, Query(ge=1, le=100)]
OffsetDep = Annotated[int, Query(ge=0)]
LIKE_ESCAPE = "\\"


def like_prefix(term: str) -> str:
    """Build a prefix LIKE pattern, escaping the metacharacters of the search term."""
    escaped = term.replace(LIKE_ESCAPE, LIKE_ESCAPE * 2).replace("%", f"{LIKE_ESCAPE}%").replace("_", f"{LIKE_ESCAPE}_")
    return f"{escaped}%"


@router.get("/search")
def search_customers(
    session: SessionDep,
    q: Annotated[str, Query(min_length=2, max_length=100)],
    limit: LimitDep = 20,
    offset: OffsetDep = 0,
) -> list[CustomerOut]:
    """Prefix search on name or email; anchored so the indexes on those columns can be used."""
    pattern = like_prefix(q)
    rows = session.execute(
        select(Customer.id, Customer.name, Customer.email)
        .where(Customer.name.like(pattern, escape=LIKE_ESCAPE) | Customer.email.like(pattern, escape=LIKE_ESCAPE))
        .order_by(Customer.name)
        .limit(limit)
        .offset(offset)
    ).all()
    return [CustomerOut(id=row.id, name=row.name, email=row.email) for row in rows]


@router.get("/{customer_id}/orders")
def customer_orders(
    session: SessionDep,
    customer_id: int,
    limit: LimitDep = 20,
    offset: OffsetDep = 0,
) -> list[OrderSummary]:
    if session.get(Customer, customer_id) is None:
        raise HTTPException(status_code=404, detail="customer not found")
    orders = session.scalars(
        select(Order)
        .where(Order.customer_id == customer_id)
        .options(selectinload(Order.lines))
        .order_by(Order.created_at.desc(), Order.id.desc())
        .limit(limit)
        .offset(offset)
    ).all()
    return [
        OrderSummary(
            id=order.id,
            created_at=order.created_at,
            line_count=len(order.lines),
            total_cents=order_total(order.lines),
        )
        for order in orders
    ]
