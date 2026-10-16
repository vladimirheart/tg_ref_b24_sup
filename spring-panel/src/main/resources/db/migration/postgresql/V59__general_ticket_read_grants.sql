-- GENERAL access is explicit and independent of business memberships/system roles.
CREATE TABLE general_ticket_read_grants (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_tickets_general_queue ON tickets(created_at DESC NULLS LAST, user_id, ticket_id)
    WHERE is_general = TRUE AND business_id IS NULL;
