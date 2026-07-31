#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


EXCLUDED_DIR_NAMES = {
    ".agents",
    ".git",
    ".venv",
    ".vscode",
    "__pycache__",
    "logs",
    "node_modules",
    "run",
    "target",
    "temp-recovery",
}

DEFAULT_STORAGE_ROOTS = (
    "attachments",
    "java-bot/attachments",
)

DEFAULT_HINT_ROOTS = {
    "applications.photo_path": ("passport_photos",),
    "chat_history.attachment": (),
    "client_avatar_history.full_path": ("avatars",),
    "client_avatar_history.thumb_path": ("avatars",),
    "knowledge_article_files.file_path": ("knowledge_base",),
    "knowledge_article_files.stored_path": ("knowledge_base",),
}


@dataclass(frozen=True)
class ResolvedReference:
    raw: str
    exists: bool
    resolution_kind: str
    resolved_path: str | None
    root_label: str | None


@dataclass(frozen=True)
class ReferenceSample:
    raw: str
    ticket_id: str | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build an Iguana storage inventory report for attachment roots, "
            "SQLite file sizes, and attachment-related references."
        )
    )
    parser.add_argument(
        "--repo-root",
        default=None,
        help="Repository root. Defaults to the parent of this script.",
    )
    parser.add_argument(
        "--storage-root",
        dest="storage_roots",
        action="append",
        default=[],
        help=(
            "Extra attachment root to inspect. Can be repeated. "
            "Defaults to ./attachments and ./java-bot/attachments."
        ),
    )
    parser.add_argument(
        "--json-out",
        default=None,
        help="Optional path to write the raw JSON report.",
    )
    parser.add_argument(
        "--markdown-out",
        default=None,
        help="Optional path to write the Markdown report.",
    )
    parser.add_argument(
        "--top",
        type=int,
        default=10,
        help="How many top areas/extensions/files to include in summaries.",
    )
    return parser.parse_args()


def repo_root_from_args(raw_repo_root: str | None) -> Path:
    if raw_repo_root:
        return Path(raw_repo_root).expanduser().resolve()
    return Path(__file__).resolve().parent.parent


def human_size(value: int) -> str:
    units = ("B", "KB", "MB", "GB", "TB")
    size = float(value)
    for unit in units:
        if size < 1024.0 or unit == units[-1]:
            return f"{size:.1f} {unit}"
        size /= 1024.0
    return f"{value} B"


def should_skip_path(path: Path) -> bool:
    return any(part in EXCLUDED_DIR_NAMES for part in path.parts)


def discover_storage_roots(repo_root: Path, extra_roots: Iterable[str]) -> list[Path]:
    seen: set[str] = set()
    roots: list[Path] = []
    for raw in list(DEFAULT_STORAGE_ROOTS) + list(extra_roots):
        candidate = (repo_root / raw).resolve()
        key = str(candidate).lower()
        if key in seen:
            continue
        seen.add(key)
        roots.append(candidate)
    return roots


def discover_sqlite_files(repo_root: Path) -> list[Path]:
    db_files: list[Path] = []
    for path in repo_root.rglob("*.db"):
        if should_skip_path(path.relative_to(repo_root)):
            continue
        db_files.append(path.resolve())
    return sorted(db_files)


def scan_storage_root(root: Path, top_n: int) -> dict:
    result = {
        "root": str(root),
        "exists": root.exists() and root.is_dir(),
        "total_files": 0,
        "total_bytes": 0,
        "by_area": [],
        "by_extension": [],
        "largest_files": [],
    }
    if not result["exists"]:
        return result

    area_counter: Counter[str] = Counter()
    ext_counter: Counter[str] = Counter()
    area_sizes: Counter[str] = Counter()
    ext_sizes: Counter[str] = Counter()
    largest: list[tuple[int, Path]] = []

    for file_path in root.rglob("*"):
        if not file_path.is_file():
            continue
        relative = file_path.relative_to(root)
        area = relative.parts[0] if relative.parts else "."
        suffix = file_path.suffix.lower() if file_path.suffix else "[no_ext]"
        size = file_path.stat().st_size

        result["total_files"] += 1
        result["total_bytes"] += size
        area_counter[area] += 1
        area_sizes[area] += size
        ext_counter[suffix] += 1
        ext_sizes[suffix] += size
        largest.append((size, relative))

    result["by_area"] = [
        {
            "name": name,
            "files": area_counter[name],
            "bytes": area_sizes[name],
        }
        for name, _ in area_sizes.most_common(top_n)
    ]
    result["by_extension"] = [
        {
            "name": name,
            "files": ext_counter[name],
            "bytes": ext_sizes[name],
        }
        for name, _ in ext_sizes.most_common(top_n)
    ]
    result["largest_files"] = [
        {"path": str(path).replace("\\", "/"), "bytes": size}
        for size, path in sorted(largest, reverse=True)[:top_n]
    ]
    return result


def query_reference_samples(connection: sqlite3.Connection) -> list[dict]:
    results: list[dict] = []
    metadata_present = has_columns(connection, "chat_attachment_metadata", ("chat_history_id",))
    if has_columns(connection, "chat_attachment_metadata", ("storage_key",)):
        results.extend(
            query_simple_column(
                connection,
                table="chat_attachment_metadata",
                column="storage_key",
            )
        )
    if has_columns(connection, "chat_history", ("attachment",)):
        if metadata_present:
            results.extend(query_chat_history_legacy_attachment_column(connection))
        else:
            results.extend(
                query_simple_column(
                    connection,
                    table="chat_history",
                    column="attachment",
                    ticket_column="ticket_id",
                )
            )

    if has_columns(connection, "knowledge_article_files", ("stored_path",)):
        results.extend(
            query_simple_column(
                connection,
                table="knowledge_article_files",
                column="stored_path",
            )
        )

    if has_columns(connection, "knowledge_article_files", ("file_path",)):
        results.extend(
            query_simple_column(
                connection,
                table="knowledge_article_files",
                column="file_path",
            )
        )

    if has_columns(connection, "client_avatar_history", ("thumb_path",)):
        results.extend(
            query_simple_column(
                connection,
                table="client_avatar_history",
                column="thumb_path",
            )
        )

    if has_columns(connection, "client_avatar_history", ("full_path",)):
        results.extend(
            query_simple_column(
                connection,
                table="client_avatar_history",
                column="full_path",
            )
        )

    if has_columns(connection, "applications", ("photo_path",)):
        results.extend(
            query_simple_column(
                connection,
                table="applications",
                column="photo_path",
            )
        )

    if has_columns(connection, "knowledge_articles", ("attachments",)):
        results.extend(query_knowledge_article_attachments(connection))

    return results


def query_chat_history_legacy_attachment_column(connection: sqlite3.Connection) -> list[dict]:
    sql = """
        SELECT ch.ticket_id, ch.attachment
          FROM chat_history ch
         WHERE ch.attachment IS NOT NULL
           AND TRIM(ch.attachment) <> ''
           AND NOT EXISTS (
               SELECT 1
                 FROM chat_attachment_metadata cam
                WHERE cam.chat_history_id = ch.id
           )
    """
    result = []
    for ticket_id, raw in connection.execute(sql):
        result.append(
            {
                "reference_key": "chat_history.attachment_legacy",
                "raw": str(raw).strip(),
                "ticket_id": str(ticket_id).strip() if ticket_id is not None else None,
            }
        )
    return result


def query_attachment_metadata_status(connection: sqlite3.Connection) -> dict | None:
    if not has_columns(connection, "chat_attachment_metadata", ("normalization_status",)):
        return None
    sql = """
        SELECT
            COUNT(*) AS total_rows,
            SUM(CASE WHEN normalization_status = 'normalized' THEN 1 ELSE 0 END) AS normalized_rows,
            SUM(CASE WHEN normalization_status = 'unresolved' THEN 1 ELSE 0 END) AS unresolved_rows
          FROM chat_attachment_metadata
    """
    row = connection.execute(sql).fetchone()
    if row is None:
        return None
    return {
        "total_rows": int(row[0] or 0),
        "normalized_rows": int(row[1] or 0),
        "unresolved_rows": int(row[2] or 0),
    }


def has_columns(connection: sqlite3.Connection, table: str, expected: tuple[str, ...]) -> bool:
    cursor = connection.execute(f"PRAGMA table_info('{table}')")
    columns = {row[1] for row in cursor.fetchall()}
    return all(column in columns for column in expected)


def query_simple_column(
    connection: sqlite3.Connection,
    table: str,
    column: str,
    ticket_column: str | None = None,
) -> list[dict]:
    if ticket_column and has_columns(connection, table, (ticket_column,)):
        sql = (
            f"SELECT {ticket_column}, {column} "
            f"FROM {table} "
            f"WHERE {column} IS NOT NULL AND TRIM({column}) <> ''"
        )
    else:
        sql = (
            f"SELECT NULL AS ticket_id, {column} "
            f"FROM {table} "
            f"WHERE {column} IS NOT NULL AND TRIM({column}) <> ''"
        )

    result = []
    for ticket_id, raw in connection.execute(sql):
        result.append(
            {
                "reference_key": f"{table}.{column}",
                "raw": str(raw).strip(),
                "ticket_id": str(ticket_id).strip() if ticket_id is not None else None,
            }
        )
    return result


def query_knowledge_article_attachments(connection: sqlite3.Connection) -> list[dict]:
    sql = (
        "SELECT attachments "
        "FROM knowledge_articles "
        "WHERE attachments IS NOT NULL AND TRIM(attachments) <> ''"
    )
    results: list[dict] = []
    for (payload,) in connection.execute(sql):
        raw_payload = str(payload).strip()
        try:
            parsed = json.loads(raw_payload)
        except json.JSONDecodeError:
            results.append(
                {
                    "reference_key": "knowledge_articles.attachments",
                    "raw": raw_payload,
                    "ticket_id": None,
                }
            )
            continue

        for extracted in extract_paths_from_payload(parsed):
            results.append(
                {
                    "reference_key": "knowledge_articles.attachments",
                    "raw": extracted,
                    "ticket_id": None,
                }
            )
    return results


def extract_paths_from_payload(value: object) -> list[str]:
    results: list[str] = []
    if isinstance(value, str):
        text = value.strip()
        if text:
            results.append(text)
        return results
    if isinstance(value, list):
        for item in value:
            results.extend(extract_paths_from_payload(item))
        return results
    if isinstance(value, dict):
        for key in (
            "storedPath",
            "stored_path",
            "filePath",
            "file_path",
            "storedName",
            "stored_name",
            "name",
        ):
            inner = value.get(key)
            if isinstance(inner, str) and inner.strip():
                results.append(inner.strip())
        return results
    return results


def resolve_reference(
    repo_root: Path,
    storage_roots: list[Path],
    reference_key: str,
    sample: ReferenceSample,
) -> ResolvedReference:
    raw = sample.raw.strip()
    normalized = raw.replace("\\", "/").strip()
    hint_paths = DEFAULT_HINT_ROOTS.get(reference_key, ())
    candidate_paths: list[tuple[Path, str, str]] = []
    fallback_candidate_paths: list[tuple[Path, str, str]] = []

    raw_path = Path(raw)
    absolute_missing: tuple[Path, str, str] | None = None
    if raw_path.is_absolute():
        normalized_absolute = raw_path.expanduser().resolve(strict=False)
        if normalized_absolute.exists() and normalized_absolute.is_file():
            return ResolvedReference(
                raw=raw,
                exists=True,
                resolution_kind="absolute",
                resolved_path=str(normalized_absolute),
                root_label=None,
            )
        absolute_missing = (normalized_absolute, "absolute-missing", "absolute")

    attachment_suffix = extract_attachments_suffix(normalized)
    if attachment_suffix is not None:
        for root in storage_roots:
            candidate_paths.append(
                (
                    (root / attachment_suffix).resolve(strict=False),
                    "rewritten-storage-root",
                    str(root),
                )
            )

    for root in storage_roots:
        if sample.ticket_id:
            candidate_paths.append(
                (
                    (root / sample.ticket_id / normalized).resolve(strict=False),
                    "ticket-storage-root",
                    str(root / sample.ticket_id),
                )
            )

    for root in storage_roots:
        candidate_paths.append(
            (
                (root / normalized).resolve(strict=False),
                "storage-root-relative",
                str(root),
            )
        )
        for hint in hint_paths:
            candidate_paths.append(
                (
                    (root / hint / normalized).resolve(strict=False),
                    "hinted-storage-root",
                    str(root / hint),
                )
            )

    if absolute_missing is not None:
        fallback_candidate_paths.append(absolute_missing)

    repo_relative = (repo_root / normalized).resolve(strict=False)
    fallback_candidate_paths.append((repo_relative, "repo-relative", "repo-root"))

    seen: set[str] = set()
    first_missing: tuple[Path, str, str] | None = None
    for path, kind, root_label in candidate_paths + fallback_candidate_paths:
        key = str(path).lower()
        if key in seen:
            continue
        seen.add(key)
        if path.exists() and path.is_file():
            return ResolvedReference(
                raw=raw,
                exists=True,
                resolution_kind=kind,
                resolved_path=str(path),
                root_label=root_label,
            )
        if first_missing is None:
            first_missing = (path, kind, root_label)

    if first_missing is not None:
        path, kind, root_label = first_missing
        return ResolvedReference(
            raw=raw,
            exists=False,
            resolution_kind=kind,
            resolved_path=str(path),
            root_label=root_label,
        )

    return ResolvedReference(
        raw=raw,
        exists=False,
        resolution_kind="unresolved",
        resolved_path=None,
        root_label=None,
    )


def extract_attachments_suffix(raw: str) -> str | None:
    lowered = raw.lower()
    marker = "/attachments/"
    index = lowered.find(marker)
    if index >= 0:
        return raw[index + len(marker):].lstrip("/").replace("\\", "/")
    if lowered.startswith("attachments/"):
        return raw[len("attachments/"):].lstrip("/").replace("\\", "/")
    return None


def summarize_references(
    repo_root: Path,
    db_path: Path,
    storage_roots: list[Path],
) -> tuple[list[dict], dict | None]:
    connection = sqlite3.connect(
        f"file:{db_path.as_posix()}?mode=ro&immutable=1",
        uri=True,
    )
    try:
        raw_samples = query_reference_samples(connection)
        attachment_metadata_status = query_attachment_metadata_status(connection)
    finally:
        connection.close()

    grouped: dict[str, list[ReferenceSample]] = {}
    for item in raw_samples:
        grouped.setdefault(item["reference_key"], []).append(
            ReferenceSample(raw=item["raw"], ticket_id=item["ticket_id"])
        )

    summaries: list[dict] = []
    for reference_key, samples in sorted(grouped.items()):
        distinct_raw = {sample.raw for sample in samples}
        existing = 0
        missing = 0
        drift = 0
        sample_existing: list[dict] = []
        sample_missing: list[dict] = []

        for sample in samples:
            resolved = resolve_reference(repo_root, storage_roots, reference_key, sample)
            if resolved.exists:
                existing += 1
                if resolved.resolution_kind == "rewritten-storage-root":
                    drift += 1
                if len(sample_existing) < 3:
                    sample_existing.append(
                        {
                            "raw": sample.raw,
                            "resolved_path": resolved.resolved_path,
                            "resolution_kind": resolved.resolution_kind,
                        }
                    )
            else:
                missing += 1
                if len(sample_missing) < 3:
                    sample_missing.append(
                        {
                            "raw": sample.raw,
                            "expected_path": resolved.resolved_path,
                            "resolution_kind": resolved.resolution_kind,
                        }
                    )

        summaries.append(
            {
                "reference_key": reference_key,
                "rows": len(samples),
                "distinct_raw_values": len(distinct_raw),
                "resolved_existing": existing,
                "resolved_missing": missing,
                "path_drift_matches": drift,
                "sample_existing": sample_existing,
                "sample_missing": sample_missing,
            }
        )

    return summaries, attachment_metadata_status


def build_report(repo_root: Path, storage_roots: list[Path], top_n: int) -> dict:
    storage = [scan_storage_root(root, top_n) for root in storage_roots]
    db_files = discover_sqlite_files(repo_root)

    databases = []
    attachment_references = []
    attachment_metadata = []
    for db_path in db_files:
        relative = db_path.relative_to(repo_root)
        databases.append(
            {
                "path": str(relative).replace("\\", "/"),
                "bytes": db_path.stat().st_size,
            }
        )
        summaries, metadata_status = summarize_references(repo_root, db_path, storage_roots)
        attachment_references.extend(
            [
                {
                    "database": str(relative).replace("\\", "/"),
                    **summary,
                }
                for summary in summaries
            ]
        )
        if metadata_status is not None:
            attachment_metadata.append(
                {
                    "database": str(relative).replace("\\", "/"),
                    **metadata_status,
                }
            )

    return {
        "generated_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "repo_root": str(repo_root),
        "storage_roots": storage,
        "databases": databases,
        "attachment_metadata": attachment_metadata,
        "attachment_references": attachment_references,
        "risks": build_risks(storage, databases, attachment_references, attachment_metadata),
    }


def build_risks(storage: list[dict], databases: list[dict], attachment_references: list[dict], attachment_metadata: list[dict]) -> list[str]:
    risks: list[str] = []
    for root in storage:
        if not root["exists"]:
            risks.append(f"Storage root missing: {root['root']}")
    for reference in attachment_references:
        if reference["resolved_missing"] > 0:
            risks.append(
                "Missing attachment references in "
                f"{reference['database']}::{reference['reference_key']} "
                f"({reference['resolved_missing']} rows)"
            )
        if reference["path_drift_matches"] > 0:
            risks.append(
                "Path drift detected in "
                f"{reference['database']}::{reference['reference_key']} "
                f"({reference['path_drift_matches']} rows)"
            )
    for metadata in attachment_metadata:
        if metadata["unresolved_rows"] > 0:
            risks.append(
                "Unresolved attachment metadata rows in "
                f"{metadata['database']} ({metadata['unresolved_rows']} rows)"
            )
    if not databases:
        risks.append("No SQLite files discovered under the repository root.")
    return risks


def render_markdown(report: dict, top_n: int) -> str:
    lines: list[str] = []
    lines.append("# Iguana Storage Inventory")
    lines.append("")
    lines.append(f"- Generated at (UTC): `{report['generated_at_utc']}`")
    lines.append(f"- Repo root: `{report['repo_root']}`")
    lines.append(f"- Top lists: `{top_n}`")
    lines.append("")

    lines.append("## Storage roots")
    lines.append("")
    lines.append("| Root | Exists | Files | Size |")
    lines.append("| --- | --- | ---: | ---: |")
    for root in report["storage_roots"]:
        lines.append(
            f"| `{root['root']}` | "
            f"{'yes' if root['exists'] else 'no'} | "
            f"{root['total_files']} | "
            f"{human_size(root['total_bytes'])} |"
        )
    lines.append("")

    for root in report["storage_roots"]:
        if not root["exists"]:
            continue
        lines.append(f"### `{root['root']}`")
        lines.append("")
        if root["by_area"]:
            lines.append("Top areas:")
            for item in root["by_area"]:
                lines.append(
                    f"- `{item['name']}`: {item['files']} files, {human_size(item['bytes'])}"
                )
            lines.append("")
        if root["by_extension"]:
            lines.append("Top extensions:")
            for item in root["by_extension"]:
                lines.append(
                    f"- `{item['name']}`: {item['files']} files, {human_size(item['bytes'])}"
                )
            lines.append("")
        if root["largest_files"]:
            lines.append("Largest files:")
            for item in root["largest_files"]:
                lines.append(f"- `{item['path']}`: {human_size(item['bytes'])}")
            lines.append("")

    lines.append("## SQLite files")
    lines.append("")
    lines.append("| Database | Size |")
    lines.append("| --- | ---: |")
    for db in report["databases"]:
        lines.append(f"| `{db['path']}` | {human_size(db['bytes'])} |")
    lines.append("")

    if report["attachment_metadata"]:
        lines.append("## Attachment metadata")
        lines.append("")
        lines.append("| Database | Total | Normalized | Unresolved |")
        lines.append("| --- | ---: | ---: | ---: |")
        for item in report["attachment_metadata"]:
            lines.append(
                f"| `{item['database']}` | {item['total_rows']} | "
                f"{item['normalized_rows']} | {item['unresolved_rows']} |"
            )
        lines.append("")

    lines.append("## Attachment references")
    lines.append("")
    lines.append("| Database | Reference | Rows | Distinct | Existing | Missing | Drift |")
    lines.append("| --- | --- | ---: | ---: | ---: | ---: | ---: |")
    for reference in report["attachment_references"]:
        lines.append(
            f"| `{reference['database']}` | `{reference['reference_key']}` | "
            f"{reference['rows']} | {reference['distinct_raw_values']} | "
            f"{reference['resolved_existing']} | {reference['resolved_missing']} | "
            f"{reference['path_drift_matches']} |"
        )
    lines.append("")

    details = [item for item in report["attachment_references"] if item["resolved_missing"] or item["path_drift_matches"]]
    if details:
        lines.append("## Sample anomalies")
        lines.append("")
        for item in details:
            lines.append(f"### `{item['database']}::{item['reference_key']}`")
            lines.append("")
            for sample in item["sample_missing"]:
                lines.append(
                    f"- Missing: raw=`{sample['raw']}` expected=`{sample['expected_path']}` "
                    f"via `{sample['resolution_kind']}`"
                )
            for sample in item["sample_existing"]:
                if sample["resolution_kind"] == "rewritten-storage-root":
                    lines.append(
                        f"- Drift recovered: raw=`{sample['raw']}` resolved=`{sample['resolved_path']}`"
                    )
            lines.append("")

    lines.append("## Risks")
    lines.append("")
    if report["risks"]:
        for risk in report["risks"]:
            lines.append(f"- {risk}")
    else:
        lines.append("- No immediate storage risks detected by this report.")
    lines.append("")

    return "\n".join(lines)


def write_text_output(path_value: str | None, content: str) -> None:
    if not path_value:
        return
    target = Path(path_value).expanduser().resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def main() -> int:
    args = parse_args()
    repo_root = repo_root_from_args(args.repo_root)
    storage_roots = discover_storage_roots(repo_root, args.storage_roots)
    report = build_report(repo_root, storage_roots, args.top)

    markdown = render_markdown(report, args.top)
    print(markdown)

    write_text_output(args.markdown_out, markdown)
    if args.json_out:
        target = Path(args.json_out).expanduser().resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    return 0


if __name__ == "__main__":
    sys.exit(main())
