"""SQLAlchemy session and engine helpers shared between panel and bots."""
from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
from typing import Callable, Iterator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, scoped_session, sessionmaker

try:
    from config import DATABASE_URL as _CONFIG_DATABASE_URL
except Exception:
    base_dir = Path(__file__).resolve().parents[1]
    _CONFIG_DATABASE_URL = f"sqlite:///{base_dir / 'tickets.db'}"

DATABASE_URL = _CONFIG_DATABASE_URL


class Base(DeclarativeBase):
    """Declarative base for ORM models."""


_engine_kwargs: dict = {"future": True}
if DATABASE_URL.startswith("sqlite"):
    _engine_kwargs.setdefault("connect_args", {"check_same_thread": False})

engine = create_engine(DATABASE_URL, **_engine_kwargs)
SessionFactory = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)
SessionLocal = scoped_session(SessionFactory)


@contextmanager
def session_scope(session_factory: Callable[[], object] | None = None) -> Iterator:
    """Provide a transactional scope around a series of operations."""

    factory = session_factory or SessionLocal
    session = factory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
