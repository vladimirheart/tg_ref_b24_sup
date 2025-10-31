CREATE TABLE chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                message TEXT,
                timestamp INTEGER,
                message_id INTEGER,
                message_type TEXT DEFAULT 'text',
                FOREIGN KEY (user_id) REFERENCES users (user_id)
            );
CREATE TABLE sqlite_sequence(name,seq);
CREATE TABLE users (
                user_id INTEGER PRIMARY KEY,
                username TEXT,
                first_name TEXT,
                last_name TEXT,
                registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
CREATE TABLE applications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                problem_description TEXT,
                photo_path TEXT,
                status TEXT DEFAULT 'new',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                b24_contact_id INTEGER,
                b24_deal_id INTEGER,
                FOREIGN KEY (user_id) REFERENCES users (user_id)
            );
