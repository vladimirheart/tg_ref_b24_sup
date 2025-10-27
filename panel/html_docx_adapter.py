"""Utilities for working with optional HTML → DOCX conversion dependencies."""

from __future__ import annotations

import importlib
import importlib.util
import logging
import re
from functools import lru_cache
from html import unescape
from typing import Protocol

from docx import Document

logger = logging.getLogger(__name__)


class SupportsHtmlToDocx(Protocol):
    """Protocol describing the converter API used by the application."""

    def add_html_to_document(self, html: str, document: Document) -> None:
        """Append the provided HTML to the given ``python-docx`` document."""


class _PlainTextHtmlConverter:
    """Fallback converter that strips HTML and keeps readable text only."""

    _BLOCK_TAG_RE = re.compile(r"<\s*/?(p|div|br|li|h[1-6]|tr|td|th|ul|ol)\b[^>]*>", re.IGNORECASE)
    _TAG_RE = re.compile(r"<[^>]+>")

    def add_html_to_document(self, html: str, document: Document) -> None:  # type: ignore[override]
        plain_text = self._to_plain_text(html)
        for line in plain_text.splitlines():
            cleaned = line.strip()
            if cleaned:
                document.add_paragraph(cleaned)

    def _to_plain_text(self, html: str) -> str:
        text = self._BLOCK_TAG_RE.sub("\n", html)
        text = self._TAG_RE.sub(" ", text)
        text = unescape(text)
        lines = [part.strip() for part in text.splitlines()]
        return "\n".join(line for line in lines if line)


@lru_cache(maxsize=1)
def build_html_to_docx_converter() -> SupportsHtmlToDocx:
    """Return a converter that can add HTML content to a ``python-docx`` document.

    If the optional :mod:`html2docx` dependency is available we delegate the
    conversion to it. Otherwise we fall back to a simple converter that strips
    the markup and keeps plain text so the export endpoint remains functional
    instead of failing with ``ModuleNotFoundError``.
    """

    module_name = "html2docx"
    if importlib.util.find_spec(module_name):
        module = importlib.import_module(module_name)
        html2docx_class = getattr(module, "Html2Docx", None)
        if html2docx_class is not None:
            return html2docx_class()
        logger.warning("Module '%s' is available but does not expose Html2Docx; falling back to plain text conversion.", module_name)

    logger.warning(
        "Optional dependency 'html2docx' is not installed. Exported DOCX files will contain plain text only."
    )
    return _PlainTextHtmlConverter()
