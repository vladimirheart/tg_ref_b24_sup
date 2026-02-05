CREATE TABLE IF NOT EXISTS ticket_categories (
    id BIGSERIAL PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    category TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_ticket_categories_ticket_category UNIQUE(ticket_id, category)
);

CREATE INDEX IF NOT EXISTS idx_ticket_categories_ticket_id ON ticket_categories(ticket_id);
