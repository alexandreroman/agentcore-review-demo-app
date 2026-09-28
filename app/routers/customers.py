from typing import Annotated

from fastapi import APIRouter, Query
from sqlalchemy import or_, select

from app.database import SessionDep
from app.models import Customer, Order, OrderLine
from app.pricing import order_total
from app.schemas import CustomerOut, OrderSummary

router = APIRouter(prefix="/customers", tags=["customers"])

Limit = Annotated[int, Query(ge=1, le=100)]
Offset = Annotated[int, Query(ge=0)]


@router.get("/search")
def search_customers(session: SessionDep, q: str, limit: Limit = 20, offset: Offset = 0) -> list[CustomerOut]:
    pattern = f"%{q}%"
    customers = session.scalars(
        select(Customer)
        .where(or_(Customer.name.like(pattern), Customer.email.like(pattern)))
        .order_by(Customer.name)
        .limit(limit)
        .offset(offset)
    ).all()
    return [CustomerOut(id=customer.id, name=customer.name, email=customer.email) for customer in customers]


@router.get("/{customer_id}/orders")
def customer_orders(session: SessionDep, customer_id: int, limit: Limit = 20, offset: Offset = 0) -> list[OrderSummary]:
    orders = session.scalars(
        select(Order)
        .where(Order.customer_id == customer_id)
        .order_by(Order.created_at.desc(), Order.id.desc())
        .limit(limit)
        .offset(offset)
    ).all()
    history = []
    for order in orders:
        lines = session.scalars(select(OrderLine).where(OrderLine.order_id == order.id)).all()
        history.append(
            OrderSummary(
                id=order.id,
                created_at=order.created_at,
                line_count=len(lines),
                total_cents=order_total(lines),
            )
        )
    return history
