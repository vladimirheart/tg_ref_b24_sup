#!/usr/bin/env python3
"""Export SQLite databases to INSERT statements compatible with PostgreSQL or MySQL."""

from __future__ import annotations

import argparse
import pathlib
import sqlite3
from typing import Iterable

BOOLEAN_COLUMNS = {
    "channels": {"is_active"},
    "client_phones": {"is_active"},
    "notifications": {"is_read"},
    "client_blacklist": {"is_blacklisted", "unblock_requested"},
    "settings_parameters": {"is_deleted"},
    "panel_users": {"is_blocked"},
}

IDENTITY_TABLES = {
    "channels": "channels_id_seq",
    "panel_users": "panel_users_id_seq",
    "roles": "roles_id_seq",
    "chat_history": "chat_history_id_seq",
    "feedbacks": "feedbacks_id_seq",
    "client_usernames": "client_usernames_id_seq",
    "client_phones": "client_phones_id_seq",
    "ticket_spans": "ticket_spans_id_seq",
    "pending_feedback_requests": "pending_feedback_requests_id_seq",
    "app_settings": "app_settings_id_seq",
    "tasks": "tasks_id_seq",
    "task_people": "task_people_id_seq",
    "task_comments": "task_comments_id_seq",
    "task_history": "task_history_id_seq",
    "notifications": "notifications_id_seq",
    "settings_parameters": "settings_parameters_id_seq",
    "it_equipment_catalog": "it_equipment_catalog_id_seq",
    "knowledge_articles": "knowledge_articles_id_seq",
    "knowledge_article_files": "knowledge_article_files_id_seq",
    "web_form_sessions": "web_form_sessions_id_seq",
    "client_unblock_requests": "client_unblock_requests_id_seq",
    "client_avatar_history": "client_avatar_history_id_seq",
    "bot_chat_history": "bot_chat_history_id_seq",
    "applications": "applications_id_seq",
}

MIGRATION_PLAN: list[tuple[str, str, str, list[tuple[str, str]]]] = [
    ("tickets.db", "channels", "channels", [
        ("id", "id"),
        ("token", "token"),
        ("bot_name", "bot_name"),
        ("channel_name", "channel_name"),
        ("questions_cfg", "questions_cfg"),
        ("max_questions", "max_questions"),
        ("is_active", "is_active"),
        ("created_at", "created_at"),
        ("bot_username", "bot_username"),
        ("question_template_id", "question_template_id"),
        ("rating_template_id", "rating_template_id"),
        ("public_id", "public_id"),
    ]),
    ("tickets.db", "tickets", "tickets", [
        ("user_id", "user_id"),
        ("ticket_id", "ticket_id"),
        ("group_msg_id", "group_msg_id"),
        ("status", "status"),
        ("resolved_at", "resolved_at"),
        ("resolved_by", "resolved_by"),
        ("channel_id", "channel_id"),
        ("reopen_count", "reopen_count"),
        ("closed_count", "closed_count"),
        ("work_time_total_sec", "work_time_total_sec"),
        ("last_reopen_at", "last_reopen_at"),
    ]),
    ("tickets.db", "messages", "messages", [
        ("group_msg_id", "group_msg_id"),
        ("user_id", "user_id"),
        ("business", "business"),
        ("location_type", "location_type"),
        ("city", "city"),
        ("location_name", "location_name"),
        ("problem", "problem"),
        ("created_at", "created_at"),
        ("username", "username"),
        ("category", "category"),
        ("ticket_id", "ticket_id"),
        ("created_date", "created_date"),
        ("created_time", "created_time"),
        ("client_name", "client_name"),
        ("client_status", "client_status"),
        ("channel_id", "channel_id"),
        ("updated_at", "updated_at"),
        ("updated_by", "updated_by"),
    ]),
    ("tickets.db", "chat_history", "chat_history", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("sender", "sender"),
        ("message", "message"),
        ("timestamp", "timestamp"),
        ("ticket_id", "ticket_id"),
        ("message_type", "message_type"),
        ("attachment", "attachment"),
        ("channel_id", "channel_id"),
        ("tg_message_id", "tg_message_id"),
        ("reply_to_tg_id", "reply_to_tg_id"),
        ("edited_at", "edited_at"),
        ("deleted_at", "deleted_at"),
    ]),
    ("tickets.db", "feedbacks", "feedbacks", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("rating", "rating"),
        ("timestamp", "timestamp"),
    ]),
    ("tickets.db", "client_statuses", "client_statuses", [
        ("user_id", "user_id"),
        ("status", "status"),
        ("updated_at", "updated_at"),
        ("updated_by", "updated_by"),
    ]),
    ("tickets.db", "client_usernames", "client_usernames", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("username", "username"),
        ("seen_at", "seen_at"),
    ]),
    ("tickets.db", "client_phones", "client_phones", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("phone", "phone"),
        ("label", "label"),
        ("source", "source"),
        ("is_active", "is_active"),
        ("created_at", "created_at"),
        ("created_by", "created_by"),
    ]),
    ("tickets.db", "ticket_spans", "ticket_spans", [
        ("id", "id"),
        ("ticket_id", "ticket_id"),
        ("span_no", "span_no"),
        ("started_at", "started_at"),
        ("ended_at", "ended_at"),
        ("duration_seconds", "duration_seconds"),
    ]),
    ("tickets.db", "pending_feedback_requests", "pending_feedback_requests", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("channel_id", "channel_id"),
        ("ticket_id", "ticket_id"),
        ("source", "source"),
        ("created_at", "created_at"),
        ("expires_at", "expires_at"),
        ("sent_at", "sent_at"),
    ]),
    ("tickets.db", "app_settings", "app_settings", [
        ("id", "id"),
        ("channel_id", "channel_id"),
        ("key", "key"),
        ("value", "value"),
    ]),
    ("tickets.db", "tasks", "tasks", [
        ("id", "id"),
        ("seq", "seq"),
        ("source", "source"),
        ("title", "title"),
        ("body_html", "body_html"),
        ("creator", "creator"),
        ("assignee", "assignee"),
        ("tag", "tag"),
        ("status", "status"),
        ("due_at", "due_at"),
        ("created_at", "created_at"),
        ("closed_at", "closed_at"),
        ("last_activity_at", "last_activity_at"),
    ]),
    ("tickets.db", "task_people", "task_people", [
        ("id", "id"),
        ("task_id", "task_id"),
        ("role", "role"),
        ("identity", "identity"),
    ]),
    ("tickets.db", "task_comments", "task_comments", [
        ("id", "id"),
        ("task_id", "task_id"),
        ("author", "author"),
        ("html", "html"),
        ("created_at", "created_at"),
    ]),
    ("tickets.db", "task_history", "task_history", [
        ("id", "id"),
        ("task_id", "task_id"),
        ("at", "at"),
        ("text", "text"),
    ]),
    ("tickets.db", "notifications", "notifications", [
        ("id", "id"),
        ("user", "user_identity"),
        ("text", "text"),
        ("url", "url"),
        ("is_read", "is_read"),
        ("created_at", "created_at"),
    ]),
    ("tickets.db", "task_seq", "task_seq", [
        ("id", "id"),
        ("val", "val"),
    ]),
    ("tickets.db", "ticket_active", "ticket_active", [
        ("ticket_id", "ticket_id"),
        ("user", "user_identity"),
        ("last_seen", "last_seen"),
    ]),
    ("tickets.db", "task_links", "task_links", [
        ("task_id", "task_id"),
        ("ticket_id", "ticket_id"),
    ]),
    ("tickets.db", "client_blacklist", "client_blacklist", [
        ("user_id", "user_id"),
        ("is_blacklisted", "is_blacklisted"),
        ("reason", "reason"),
        ("added_at", "added_at"),
        ("added_by", "added_by"),
        ("unblock_requested", "unblock_requested"),
        ("unblock_requested_at", "unblock_requested_at"),
    ]),
    ("tickets.db", "settings_parameters", "settings_parameters", [
        ("id", "id"),
        ("param_type", "param_type"),
        ("value", "value"),
        ("created_at", "created_at"),
        ("state", "state"),
        ("is_deleted", "is_deleted"),
        ("deleted_at", "deleted_at"),
        ("extra_json", "extra_json"),
    ]),
    ("tickets.db", "it_equipment_catalog", "it_equipment_catalog", [
        ("id", "id"),
        ("equipment_type", "equipment_type"),
        ("equipment_vendor", "equipment_vendor"),
        ("equipment_model", "equipment_model"),
        ("photo_url", "photo_url"),
        ("serial_number", "serial_number"),
        ("accessories", "accessories"),
        ("created_at", "created_at"),
        ("updated_at", "updated_at"),
    ]),
    ("tickets.db", "knowledge_articles", "knowledge_articles", [
        ("id", "id"),
        ("title", "title"),
        ("department", "department"),
        ("article_type", "article_type"),
        ("status", "status"),
        ("author", "author"),
        ("direction", "direction"),
        ("direction_subtype", "direction_subtype"),
        ("summary", "summary"),
        ("content", "content"),
        ("attachments", "attachments"),
        ("created_at", "created_at"),
        ("updated_at", "updated_at"),
    ]),
    ("tickets.db", "knowledge_article_files", "knowledge_article_files", [
        ("id", "id"),
        ("article_id", "article_id"),
        ("draft_token", "draft_token"),
        ("stored_path", "stored_path"),
        ("original_name", "original_name"),
        ("mime_type", "mime_type"),
        ("file_size", "file_size"),
        ("uploaded_at", "uploaded_at"),
    ]),
    ("tickets.db", "web_form_sessions", "web_form_sessions", [
        ("id", "id"),
        ("token", "token"),
        ("ticket_id", "ticket_id"),
        ("channel_id", "channel_id"),
        ("user_id", "user_id"),
        ("answers_json", "answers_json"),
        ("client_name", "client_name"),
        ("client_contact", "client_contact"),
        ("username", "username"),
        ("created_at", "created_at"),
        ("last_active_at", "last_active_at"),
    ]),
    ("tickets.db", "ticket_responsibles", "ticket_responsibles", [
        ("ticket_id", "ticket_id"),
        ("responsible", "responsible"),
        ("assigned_at", "assigned_at"),
        ("assigned_by", "assigned_by"),
    ]),
    ("tickets.db", "client_unblock_requests", "client_unblock_requests", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("channel_id", "channel_id"),
        ("reason", "reason"),
        ("created_at", "created_at"),
        ("status", "status"),
        ("decided_at", "decided_at"),
        ("decided_by", "decided_by"),
        ("decision_comment", "decision_comment"),
    ]),
    ("tickets.db", "client_avatar_history", "client_avatar_history", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("fingerprint", "fingerprint"),
        ("source", "source"),
        ("file_unique_id", "file_unique_id"),
        ("file_id", "file_id"),
        ("thumb_path", "thumb_path"),
        ("full_path", "full_path"),
        ("width", "width"),
        ("height", "height"),
        ("file_size", "file_size"),
        ("fetched_at", "fetched_at"),
        ("last_seen_at", "last_seen_at"),
        ("metadata", "metadata"),
    ]),
    ("users.db", "users", "panel_users", [
        ("id", "id"),
        ("username", "username"),
        ("password_hash", "password_hash"),
        ("password", "password"),
        ("role", "role"),
        ("role_id", "role_id"),
        ("photo", "photo"),
        ("registration_date", "registration_date"),
        ("birth_date", "birth_date"),
        ("email", "email"),
        ("department", "department"),
        ("phones", "phones"),
        ("full_name", "full_name"),
        ("is_blocked", "is_blocked"),
    ]),
    ("users.db", "roles", "roles", [
        ("id", "id"),
        ("name", "name"),
        ("description", "description"),
        ("permissions", "permissions"),
    ]),
    ("bot_database.db", "users", "bot_users", [
        ("user_id", "user_id"),
        ("username", "username"),
        ("first_name", "first_name"),
        ("last_name", "last_name"),
        ("registered_at", "registered_at"),
    ]),
    ("bot_database.db", "chat_history", "bot_chat_history", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("message", "message"),
        ("timestamp", "timestamp"),
        ("message_id", "message_id"),
        ("message_type", "message_type"),
    ]),
    ("bot_database.db", "applications", "applications", [
        ("id", "id"),
        ("user_id", "user_id"),
        ("problem_description", "problem_description"),
        ("photo_path", "photo_path"),
        ("status", "status"),
        ("created_at", "created_at"),
        ("b24_contact_id", "b24_contact_id"),
        ("b24_deal_id", "b24_deal_id"),
    ]),
]


def quote(value, dialect: str, boolean: bool) -> str:
    if value is None:
        return "NULL"
    if boolean:
        if isinstance(value, str):
            value = value.strip()
            if value == "":
                return "NULL"
        truthy = {"1", "true", "TRUE", "yes", "YES", 1, True}
        return "TRUE" if value in truthy else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value)
    if dialect == "mysql":
        text = text.replace("\\", "\\\\").replace("'", "\\'")
    else:
        text = text.replace("'", "''")
    return f"'{text}'"


def generate_inserts(sqlite_path: pathlib.Path, table: str, target: str,
                     column_pairs: Iterable[tuple[str, str]], dialect: str) -> Iterable[str]:
    connection = sqlite3.connect(sqlite_path)
    connection.row_factory = sqlite3.Row
    cursor = connection.cursor()
    source_columns = ", ".join(src for src, _ in column_pairs)
    cursor.execute(f"SELECT {source_columns} FROM {table}")
    boolean_columns = BOOLEAN_COLUMNS.get(target, set())
    for row in cursor.fetchall():
        columns = []
        values = []
        for src, dest in column_pairs:
            columns.append(dest)
            values.append(quote(row[src], dialect, dest in boolean_columns))
        column_sql = ", ".join(columns)
        values_sql = ", ".join(values)
        yield f"INSERT INTO {target} ({column_sql}) VALUES ({values_sql});"
    cursor.close()
    connection.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Export SQLite data to SQL INSERT statements.")
    parser.add_argument("--workspace", type=pathlib.Path, default=pathlib.Path("."),
                        help="Path to the project root containing SQLite databases")
    parser.add_argument("--dialect", choices=["postgresql", "mysql"], default="postgresql",
                        help="Target SQL dialect")
    parser.add_argument("--output", type=pathlib.Path, required=True,
                        help="Output SQL file path")
    args = parser.parse_args()

    lines: list[str] = ["-- Generated from SQLite databases", f"-- Dialect: {args.dialect}", "\nBEGIN;"]

    for db_name, table, target, columns in MIGRATION_PLAN:
        sqlite_path = args.workspace / db_name
        if not sqlite_path.exists():
            raise SystemExit(f"SQLite database not found: {sqlite_path}")
        lines.append(f"\n-- Data from {db_name}.{table}")
        lines.extend(generate_inserts(sqlite_path, table, target, columns, args.dialect))

    if args.dialect == "postgresql":
        lines.append("\n-- Reset identity sequences to the maximum imported values")
        for table, sequence in IDENTITY_TABLES.items():
            lines.append(f"SELECT setval('{sequence}', COALESCE((SELECT MAX(id) FROM {table}), 0));")

    lines.append("COMMIT;\n")

    args.output.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
