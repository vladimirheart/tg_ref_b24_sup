-- Persistent revocation applies to every web replica and survives restarts.
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT users_session_version_nonnegative CHECK (session_version >= 0);
