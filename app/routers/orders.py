from fastapi import APIRouter, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.database import SessionDep
from app.models import Customer, Order, OrderLine
from app.pricing import order_total
from app.schemas import OrderIn, OrderLineOut, OrderOut

router = APIRouter(prefix="/orders", tags=["orders"])


def to_out(order: Order) -> OrderOut:
    return OrderOut(
        id=order.id,
        customer_id=order.customer_id,
        created_at=order.created_at,
        lines=[OrderLineOut.model_validate(line) for line in order.lines],
        total_cents=order_total(order.lines),
    )


@router.get("/{order_id}")
def get_order(session: SessionDep, order_id: int) -> OrderOut:
    order = session.scalar(select(Order).where(Order.id == order_id).options(selectinload(Order.lines)))
    if order is None:
        raise HTTPException(status_code=404, detail="order not found")
    return to_out(order)


@router.post("", status_code=201)
def create_order(session: SessionDep, payload: OrderIn) -> OrderOut:
    if session.get(Customer, payload.customer_id) is None:
        raise HTTPException(status_code=404, detail="customer not found")
    order = Order(customer_id=payload.customer_id, lines=[OrderLine(**line.model_dump()) for line in payload.lines])
    session.add(order)
    session.commit()
    session.refresh(order)
    return to_out(order)
