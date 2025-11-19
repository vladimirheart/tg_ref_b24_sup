"""Ticket service exposing DTOs for panel/bot consumers."""
from __future__ import annotations

from typing import Any, Mapping

from .dto import TicketStatusStats, TicketSummary
from .repositories.tickets import TicketRepository


class TicketService:
    """Thin service layer above TicketRepository providing DTOs."""

    def __init__(self, repository: TicketRepository | None = None) -> None:
        self.repository = repository or TicketRepository()

    def list_recent(self, limit: int = 20) -> list[TicketSummary]:
        return [self._to_summary(ticket) for ticket in self.repository.list_recent(limit)]

    def status_summary(self) -> TicketStatusStats:
        return TicketStatusStats(counts=self.repository.count_by_status())

    @staticmethod
    def _to_summary(ticket: Any) -> TicketSummary:
        if isinstance(ticket, Mapping):
            source = ticket
            return TicketSummary(
                ticket_id=source.get("ticket_id"),
                user_id=source.get("user_id"),
                status=source.get("status"),
                resolved_at=source.get("resolved_at"),
                resolved_by=source.get("resolved_by"),
                channel_id=source.get("channel_id"),
                reopen_count=source.get("reopen_count"),
                closed_count=source.get("closed_count"),
            )
        return TicketSummary(
            ticket_id=ticket.ticket_id,
            user_id=ticket.user_id,
            status=ticket.status,
            resolved_at=ticket.resolved_at,
            resolved_by=ticket.resolved_by,
            channel_id=ticket.channel_id,
            reopen_count=ticket.reopen_count,
            closed_count=ticket.closed_count,
        )
