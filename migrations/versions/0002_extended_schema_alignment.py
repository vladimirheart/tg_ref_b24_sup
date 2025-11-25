"""Align schema with Java bot extended tables."""
from __future__ import annotations

from alembic import op
import sqlalchemy as sa

revision = "0002_extended_schema_alignment"
down_revision = "0001_initial_schema"
branch_labels = None
depends_on = None


def _table_exists(conn, name: str) -> bool:
    query = sa.text("SELECT name FROM sqlite_master WHERE type='table' AND name=:name")
    return conn.execute(query, {"name": name}).fetchone() is not None


def _column_exists(conn, table: str, column: str) -> bool:
    pragma = sa.text(f"PRAGMA table_info({table})")
    rows = conn.execute(pragma).fetchall()
    return any(row[1] == column for row in rows)


def _add_column(conn, table: str, column: str, ddl: str) -> None:
    op.execute(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}")


def _ensure_setting_key(conn) -> None:
    if not _table_exists(conn, "app_settings"):
        return

    has_setting_key = _column_exists(conn, "app_settings", "setting_key")
    has_key = _column_exists(conn, "app_settings", "key")

    if not has_setting_key:
        _add_column(conn, "app_settings", "setting_key", "TEXT")
        if has_key:
            op.execute("UPDATE app_settings SET setting_key = key WHERE setting_key IS NULL")
        op.execute(sa.text("UPDATE app_settings SET setting_key = '' WHERE setting_key IS NULL"))

    op.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_app_settings_channel_setting ON app_settings(channel_id, setting_key)"
    )


def _ensure_it_equipment_catalog(conn) -> None:
    if not _table_exists(conn, "it_equipment_catalog"):
        return

    has_item_type = _column_exists(conn, "it_equipment_catalog", "item_type")
    has_brand = _column_exists(conn, "it_equipment_catalog", "brand")
    has_equipment_type = _column_exists(conn, "it_equipment_catalog", "equipment_type")
    has_equipment_vendor = _column_exists(conn, "it_equipment_catalog", "equipment_vendor")

    if not has_item_type:
        _add_column(conn, "it_equipment_catalog", "item_type", "TEXT")
        if has_equipment_type:
            op.execute(
                "UPDATE it_equipment_catalog SET item_type = equipment_type WHERE item_type IS NULL"
            )

    if not has_brand:
        _add_column(conn, "it_equipment_catalog", "brand", "TEXT")
        if has_equipment_vendor:
            op.execute(
                "UPDATE it_equipment_catalog SET brand = equipment_vendor WHERE brand IS NULL"
            )

    if not _column_exists(conn, "it_equipment_catalog", "equipment_model"):
        _add_column(conn, "it_equipment_catalog", "equipment_model", "TEXT")


def upgrade() -> None:
    conn = op.get_bind()
    _ensure_setting_key(conn)
    _ensure_it_equipment_catalog(conn)


def downgrade() -> None:
    raise RuntimeError("Downgrade is not supported for schema alignment")
