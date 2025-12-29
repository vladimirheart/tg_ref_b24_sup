ALTER TABLE users ADD COLUMN IF NOT EXISTS role_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_users_role_id ON users(role_id);
