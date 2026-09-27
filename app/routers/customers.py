from typing import Annotated

from fastapi import APIRouter, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.database import SessionDep
from app.models import Customer, Order
from app.pricing import order_total
from app.schemas import CustomerOut, OrderSummary

router = APIRouter(prefix="/customers", tags=["customers"])

LIKE_ESCAPE = "\\"

# Shared, declarative pagination bounds: FastAPI answers 422 on its own.
LimitQuery = Annotated[int, Query(ge=1, le=100)]
OffsetQuery = Annotated[int, Query(ge=0)]


def like_prefix(term: str) -> str:
    """LIKE pattern matching `term` at the start, with the wildcards in `term` escaped."""
    escaped = term
    for char in (LIKE_ESCAPE, "%", "_"):
        escaped = escaped.replace(char, LIKE_ESCAPE + char)
    return f"{escaped}%"


def mask_email(email: str) -> str:
    """Hide most of an address, e.g. ada@example.com becomes a**@example.com."""
    local, separator, domain = email.partition("@")
    return f"{local[:1]}{'*' * (len(local) - 1)}{separator}{domain}"


@router.get("/search")
def search_customers(
    session: SessionDep,
    q: Annotated[str, Query(min_length=3, max_length=100)],
    limit: LimitQuery = 20,
    offset: OffsetQuery = 0,
) -> list[CustomerOut]:
    """Find customers whose name or email starts with `q`; emails come back masked."""
    pattern = like_prefix(q)
    rows = session.execute(
        select(Customer.id, Customer.name, Customer.email)
        .where(
            Customer.name.like(pattern, escape=LIKE_ESCAPE) | Customer.email.like(pattern, escape=LIKE_ESCAPE)
        )
        .order_by(Customer.name, Customer.id)
        .limit(limit)
        .offset(offset)
    ).all()
    return [CustomerOut(id=row.id, name=row.name, email=mask_email(row.email)) for row in rows]


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
