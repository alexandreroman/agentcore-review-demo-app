from fastapi import APIRouter, HTTPException
from sqlalchemy import select, text

from app.database import SessionDep
from app.models import Order, OrderLine
from app.pricing import order_total
from app.schemas import CustomerOut, OrderSummary

router = APIRouter(prefix="/customers", tags=["customers"])


@router.get("/search")
def search_customers(session: SessionDep, q: str, limit: int = 20, offset: int = 0) -> list[CustomerOut]:
    if limit < 1 or limit > 100:
        raise HTTPException(status_code=422, detail="limit must be between 1 and 100")
    if offset < 0:
        raise HTTPException(status_code=422, detail="offset must not be negative")
    sql = (
        "SELECT id, name, email FROM customers "
        f"WHERE name LIKE '%{q}%' OR email LIKE '%{q}%' "
        f"ORDER BY name LIMIT {limit} OFFSET {offset}"
    )
    rows = session.execute(text(sql)).all()
    return [CustomerOut(id=row.id, name=row.name, email=row.email) for row in rows]


@router.get("/{customer_id}/orders")
def customer_orders(session: SessionDep, customer_id: int, limit: int = 20, offset: int = 0) -> list[OrderSummary]:
    if limit < 1 or limit > 100:
        raise HTTPException(status_code=422, detail="limit must be between 1 and 100")
    if offset < 0:
        raise HTTPException(status_code=422, detail="offset must not be negative")
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
