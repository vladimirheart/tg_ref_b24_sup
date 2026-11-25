-- Extend the existing queue, keeping its IDs, decisions and historical rows.
ALTER TABLE password_reset_requests
    ADD COLUMN request_kind TEXT NOT NULL DEFAULT 'password',
    ADD COLUMN requester_name TEXT,
    ADD COLUMN requester_contact TEXT,
    ADD COLUMN correlation_id TEXT,
    ADD COLUMN connection_ip TEXT,
    ADD COLUMN lookup_result TEXT NOT NULL DEFAULT 'FOUND',
    ADD COLUMN dedupe_key TEXT,
    ADD COLUMN repeat_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN last_requested_at TIMESTAMPTZ,
    ADD COLUMN merged_into BIGINT REFERENCES password_reset_requests(id);

UPDATE password_reset_requests
SET dedupe_key = CASE WHEN user_id IS NULL THEN 'login:' || lower(COALESCE(requested_by_username, username_snapshot))
                      ELSE 'account:' || user_id::text END,
    last_requested_at = created_at,
    lookup_result = CASE WHEN user_id IS NULL THEN 'UNKNOWN' ELSE 'FOUND' END,
    status = CASE WHEN user_id IS NULL AND status = 'PENDING' THEN 'NEEDS_REVIEW' ELSE status END;

-- Consolidate old pending duplicates without deleting their history.
WITH pending AS (
    SELECT id, min(id) OVER (PARTITION BY dedupe_key) AS canonical_id,
           count(*) OVER (PARTITION BY dedupe_key) AS total
    FROM password_reset_requests WHERE status IN ('PENDING', 'NEEDS_REVIEW')
)
UPDATE password_reset_requests r
SET repeat_count = p.total,
    merged_into = CASE WHEN r.id <> p.canonical_id THEN p.canonical_id ELSE NULL END,
    status = CASE WHEN r.id <> p.canonical_id THEN 'MERGED' ELSE r.status END,
    resolved_at = CASE WHEN r.id <> p.canonical_id THEN CURRENT_TIMESTAMP ELSE r.resolved_at END
FROM pending p WHERE r.id = p.id;

CREATE UNIQUE INDEX uq_access_recovery_pending_login ON password_reset_requests(dedupe_key)
    WHERE status IN ('PENDING', 'NEEDS_REVIEW');

CREATE TABLE access_recovery_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES password_reset_requests(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    actor TEXT NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_access_recovery_events_request ON access_recovery_events(request_id, id);

-- Shared by all web replicas. Keys are digests, never raw credentials or cookies.
CREATE TABLE public_auth_rate_limits (
    scope TEXT NOT NULL,
    key_digest TEXT NOT NULL,
    window_start BIGINT NOT NULL,
    hits INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY(scope, key_digest, window_start)
);

CREATE TABLE public_auth_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    result TEXT NOT NULL,
    principal_digest TEXT NOT NULL,
    source_ip TEXT,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_public_auth_events_created ON public_auth_events(created_at);

-- Presence records a personal view without resolving the underlying request.
CREATE TABLE attention_request_seen (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('unblock', 'recovery')),
    request_id BIGINT NOT NULL,
    seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, kind, request_id)
);
