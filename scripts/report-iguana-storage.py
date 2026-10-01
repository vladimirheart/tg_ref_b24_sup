#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


DEFAULT_STORAGE_ROOTS = (
    "attachments",
    "java-bot/attachments",
)

ENV_STORAGE_ROOTS = (
    "APP_STORAGE_ATTACHMENTS",
    "APP_STORAGE_KNOWLEDGE_BASE",
    "APP_STORAGE_AVATARS",
    "APP_STORAGE_WEBFORMS",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build an Iguana filesystem storage inventory for configured storage roots, "
            "file counts, sizes, extensions and largest files."
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
            "Extra filesystem storage root to inspect. Can be repeated. "
            "Relative paths are resolved against the repository root."
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
        help="How many top extensions and largest files to include.",
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


def resolve_root(repo_root: Path, raw: str) -> Path:
    candidate = Path(raw).expanduser()
    if not candidate.is_absolute():
        candidate = repo_root / candidate
    return candidate.resolve(strict=False)


def discover_storage_roots(repo_root: Path, extra_roots: Iterable[str]) -> list[Path]:
    candidates: list[str] = list(DEFAULT_STORAGE_ROOTS)
    for env_key in ENV_STORAGE_ROOTS:
        value = os.getenv(env_key, "").strip()
        if value:
            candidates.append(value)
    candidates.extend(extra_roots)

    seen: set[str] = set()
    roots: list[Path] = []
    for raw in candidates:
        candidate = resolve_root(repo_root, raw)
        key = str(candidate).casefold()
        if key in seen:
            continue
        seen.add(key)
        roots.append(candidate)
    return roots


def display_path(path: Path, repo_root: Path) -> str:
    try:
        return str(path.relative_to(repo_root)).replace("\\", "/")
    except ValueError:
        return str(path)


def scan_storage_root(root: Path, repo_root: Path, top_n: int) -> dict:
    result = {
        "root": str(root),
        "display_root": display_path(root, repo_root),
        "exists": root.exists() and root.is_dir(),
        "total_files": 0,
        "total_bytes": 0,
        "top_extensions": [],
        "largest_files": [],
    }
    if not result["exists"]:
        return result

    extension_files: Counter[str] = Counter()
    extension_bytes: Counter[str] = Counter()
    largest: list[tuple[int, str]] = []

    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            size = path.stat().st_size
        except OSError:
            continue
        result["total_files"] += 1
        result["total_bytes"] += size
        suffix = path.suffix.lower() or "<no extension>"
        extension_files[suffix] += 1
        extension_bytes[suffix] += size
        largest.append((size, display_path(path, repo_root)))

    top_extensions = sorted(
        extension_files,
        key=lambda ext: (-extension_bytes[ext], -extension_files[ext], ext),
    )[:top_n]
    result["top_extensions"] = [
        {
            "extension": ext,
            "files": extension_files[ext],
            "bytes": extension_bytes[ext],
        }
        for ext in top_extensions
    ]
    result["largest_files"] = [
        {"path": path, "bytes": size}
        for size, path in sorted(largest, key=lambda item: (-item[0], item[1]))[:top_n]
    ]
    return result


def build_risks(storage_roots: list[dict]) -> list[str]:
    return [
        f"Storage root missing: {item['display_root']}"
        for item in storage_roots
        if not item["exists"]
    ]


def build_report(repo_root: Path, storage_roots: list[Path], top_n: int) -> dict:
    storage = [scan_storage_root(root, repo_root, top_n) for root in storage_roots]
    return {
        "generated_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "repo_root": str(repo_root),
        "inventory_scope": "filesystem-storage-roots",
        "storage_roots": storage,
        "risks": build_risks(storage),
    }


def render_markdown(report: dict, top_n: int) -> str:
    lines: list[str] = []
    lines.append("# Iguana Storage Inventory")
    lines.append("")
    lines.append(f"- Generated UTC: `{report['generated_at_utc']}`")
    lines.append(f"- Repo root: `{report['repo_root']}`")
    lines.append("- Scope: filesystem storage roots only")
    lines.append(f"- Top lists: `{top_n}`")
    lines.append("")
    lines.append("## Storage roots")
    lines.append("")
    lines.append("| Root | Exists | Files | Size |")
    lines.append("| --- | --- | ---: | ---: |")
    for root in report["storage_roots"]:
        lines.append(
            f"| `{root['display_root']}` | "
            f"{'yes' if root['exists'] else 'no'} | "
            f"{root['total_files']} | {human_size(root['total_bytes'])} |"
        )
    lines.append("")

    for root in report["storage_roots"]:
        if not root["exists"]:
            continue
        lines.append(f"### `{root['display_root']}`")
        lines.append("")
        if root["top_extensions"]:
            lines.append("Top extensions by bytes:")
            for item in root["top_extensions"]:
                lines.append(
                    f"- `{item['extension']}`: {item['files']} files, {human_size(item['bytes'])}"
                )
            lines.append("")
        if root["largest_files"]:
            lines.append("Largest files:")
            for item in root["largest_files"]:
                lines.append(f"- `{item['path']}`: {human_size(item['bytes'])}")
            lines.append("")

    lines.append("## Risks")
    lines.append("")
    if report["risks"]:
        for risk in report["risks"]:
            lines.append(f"- {risk}")
    else:
        lines.append("- No filesystem storage-root risks detected.")
    lines.append("")
    return "\n".join(lines)


def write_text_output(path_value: str | None, content: str) -> None:
    if not path_value:
        return
    target = Path(path_value).expanduser().resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)


def main() -> int:
    args = parse_args()
    repo_root = repo_root_from_args(args.repo_root)
    top_n = max(1, min(100, int(args.top)))
    storage_roots = discover_storage_roots(repo_root, args.storage_roots)
    report = build_report(repo_root, storage_roots, top_n)
    markdown = render_markdown(report, top_n)

    write_text_output(args.markdown_out, markdown)
    if args.json_out:
        write_text_output(args.json_out, json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
