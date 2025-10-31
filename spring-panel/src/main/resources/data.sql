INSERT INTO tickets (business, city, status)
VALUES
    ('Franchise', 'Москва', 'open'),
    ('Franchise', 'Москва', 'resolved'),
    ('Own', 'Санкт-Петербург', 'open');

INSERT INTO client_stats (username, last_contact, tickets)
VALUES
    ('client1', CURRENT_TIMESTAMP, 5),
    ('client2', CURRENT_TIMESTAMP - INTERVAL '1 DAY', 3);
