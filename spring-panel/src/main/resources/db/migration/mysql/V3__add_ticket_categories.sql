CREATE TABLE IF NOT EXISTS ticket_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY ux_ticket_categories_ticket_category (ticket_id, category),
    INDEX idx_ticket_categories_ticket_id (ticket_id)
);
