from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class OrderLineIn(BaseModel):
    product: str = Field(min_length=1)
    quantity: int = Field(gt=0)
    unit_price_cents: int = Field(ge=0)


class OrderIn(BaseModel):
    customer_id: int
    lines: list[OrderLineIn] = Field(min_length=1)


class OrderLineOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    product: str
    quantity: int
    unit_price_cents: int


class OrderOut(BaseModel):
    id: int
    customer_id: int
    created_at: datetime
    lines: list[OrderLineOut]
    total_cents: int


class CustomerOut(BaseModel):
    id: int
    name: str
    email: str


class OrderSummary(BaseModel):
    id: int
    created_at: datetime
    line_count: int
    total_cents: int
