CREATE TABLE users (
        id INTEGER PRIMARY KEY,
        username TEXT UNIQUE,
        password TEXT,
        role TEXT DEFAULT 'admin'
    , password_hash VARCHAR(255), role_id INTEGER, photo TEXT, registration_date TEXT, birth_date TEXT, email TEXT, department TEXT, phones TEXT, full_name TEXT, is_blocked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                description TEXT,
                permissions TEXT NOT NULL DEFAULT '{}'
            );
CREATE TABLE sqlite_sequence(name,seq);
CREATE UNIQUE INDEX idx_roles_name ON roles(name);
CREATE INDEX idx_users_role_id ON users(role_id);
