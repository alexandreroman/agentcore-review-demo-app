from typing import Annotated

from fastapi import APIRouter, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.database import SessionDep
from app.models import Customer, Order
from app.pricing import order_total
from app.schemas import CustomerOut, OrderSummary

router = APIRouter(prefix="/customers", tags=["customers"])

# Shared pagination bounds, validated by FastAPI (422 on violation).
LimitQuery = Annotated[int, Query(ge=1, le=100)]
OffsetQuery = Annotated[int, Query(ge=0)]

LIKE_ESCAPE = "\\"


def prefix_pattern(value: str) -> str:
    """LIKE pattern matching `value` at the start of a column, wildcards taken literally."""
    escaped = value.replace(LIKE_ESCAPE, LIKE_ESCAPE * 2).replace("%", f"{LIKE_ESCAPE}%").replace("_", f"{LIKE_ESCAPE}_")
    return f"{escaped}%"


@router.get("/search")
def search_customers(
    session: SessionDep,
    q: Annotated[str, Query(min_length=1, max_length=100)],
    limit: LimitQuery = 20,
    offset: OffsetQuery = 0,
) -> list[CustomerOut]:
    """Customers whose name or email starts with `q`.

    The match is a prefix one so that the indexes on `customers.name` and
    `customers.email` can be used; a leading wildcard would scan the table.
    """
    pattern = prefix_pattern(q)
    rows = session.execute(
        select(Customer.id, Customer.name, Customer.email)
        .where(
            Customer.name.like(pattern, escape=LIKE_ESCAPE) | Customer.email.like(pattern, escape=LIKE_ESCAPE)
        )
        .order_by(Customer.name)
        .limit(limit)
        .offset(offset)
    ).all()
    return [CustomerOut(id=row.id, name=row.name, email=row.email) for row in rows]


@router.get("/{customer_id}/orders")
def customer_orders(
    session: SessionDep,
    customer_id: int,
    limit: LimitQuery = 20,
    offset: OffsetQuery = 0,
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
