from collections.abc import Iterator
from typing import Annotated

from fastapi import Depends
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

# FastAPI runs sync endpoints in a thread pool, hence check_same_thread=False.
engine = create_engine("sqlite:///orders.db", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(engine)


class Base(DeclarativeBase):
    pass


def get_session() -> Iterator[Session]:
    with SessionLocal() as session:
        yield session


SessionDep = Annotated[Session, Depends(get_session)]
