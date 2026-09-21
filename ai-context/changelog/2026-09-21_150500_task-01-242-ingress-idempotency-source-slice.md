# 01-242 ingress idempotency source slice

Date: 2026-09-21

## Evidence

- Read-only production review confirmed healthy single runtime ownership and zero legacy static bots.
- Historical PostgreSQL data contains duplicate Telegram client-history rows for identical provider message ids.
- Historical inbound inbox data contains duplicate active-ticket events with distinct event ids for the same provider identity.
- Current process logs did not reproduce duplicate Telegram update ids or timeout/session-conflict signals during the review window.

## Source changes

- Telegram long-poll updates are claimed through the shared delivery guard before business side effects; successful updates are marked processed and failed runtime paths release the claim.
- Conversation finalization carries a stable provider-event key. Telegram uses chat + provider message id; MAX reuses its existing delivery key.
- Ticket and ticket-created event ids become deterministic when provider identity exists.
- integration_transport_outbox uses ON CONFLICT(event_id) DO NOTHING, turning its existing primary key into the atomic durable duplicate boundary.
- Duplicate ticket finalization returns status duplicate and skips repeated Telegram/MAX confirmation/support messages.
- Regression coverage is added for provider identity, duplicate outbox insert and Telegram delivery identity/source ownership.

## Non-goals

- No production rollout in this apply step.
- No database migration or data rewrite.
- Legacy production Compose hard-disable remains a separate 01-242 topology slice after ingress behavior is proven.
