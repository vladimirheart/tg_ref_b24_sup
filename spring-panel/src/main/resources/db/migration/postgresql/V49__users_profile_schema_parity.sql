-- Restore the active users table profile fields expected by user management.
-- Keep password and role identity canonical in users.password and users.role_id.

ALTER TABLE users ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phones TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET registration_date = created_at
WHERE registration_date IS NULL
  AND created_at IS NOT NULL;
