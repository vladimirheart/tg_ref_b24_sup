from __future__ import annotations

import sqlite3
from pathlib import Path

from scripts.export_sqlite_to_sql import generate_inserts


def _create_db(tmp_path: Path, ddl: str) -> Path:
    db_path = tmp_path / "test.db"
    conn = sqlite3.connect(db_path)
    conn.execute(ddl)
    conn.commit()
    return db_path


def test_app_settings_aliases_are_exported(tmp_path: Path) -> None:
    db_path = _create_db(
        tmp_path,
        """
        CREATE TABLE app_settings (
            id INTEGER PRIMARY KEY,
            channel_id INTEGER NOT NULL,
            key TEXT,
            value TEXT
        )
        """,
    )
    conn = sqlite3.connect(db_path)
    conn.execute(
        "INSERT INTO app_settings(id, channel_id, key, value) VALUES (1, 99, 'greeting', 'hello')"
    )
    conn.commit()
    conn.close()

    inserts = list(
        generate_inserts(
            db_path,
            "app_settings",
            "app_settings",
            [("id", "id"), ("channel_id", "channel_id"), ("setting_key", "setting_key"), ("value", "value")],
            "postgresql",
        )
    )

    assert "setting_key" in inserts[0]
    assert "'greeting'" in inserts[0]


def test_it_equipment_catalog_aliases_are_exported(tmp_path: Path) -> None:
    db_path = _create_db(
        tmp_path,
        """
        CREATE TABLE it_equipment_catalog (
            id INTEGER PRIMARY KEY,
            equipment_type TEXT,
            equipment_vendor TEXT,
            equipment_model TEXT,
            photo_url TEXT,
            serial_number TEXT,
            accessories TEXT,
            created_at TEXT,
            updated_at TEXT
        )
        """,
    )
    conn = sqlite3.connect(db_path)
    conn.execute(
        """
        INSERT INTO it_equipment_catalog(
            id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories, created_at, updated_at
        ) VALUES (1, 'Laptop', 'ACME', 'ModelX', NULL, NULL, NULL, '2024-01-01', '2024-01-02')
        """
    )
    conn.commit()
    conn.close()

    inserts = list(
        generate_inserts(
            db_path,
            "it_equipment_catalog",
            "it_equipment_catalog",
            [
                ("id", "id"),
                ("item_type", "item_type"),
                ("brand", "brand"),
                ("equipment_model", "equipment_model"),
                ("photo_url", "photo_url"),
                ("serial_number", "serial_number"),
                ("accessories", "accessories"),
                ("created_at", "created_at"),
                ("updated_at", "updated_at"),
            ],
            "postgresql",
        )
    )

    assert "item_type" in inserts[0]
    assert "'Laptop'" in inserts[0]
    assert "'ACME'" in inserts[0]
