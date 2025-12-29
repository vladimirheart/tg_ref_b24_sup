ALTER TABLE users ADD COLUMN role_id BIGINT NULL;
CREATE INDEX idx_users_role_id ON users(role_id);
