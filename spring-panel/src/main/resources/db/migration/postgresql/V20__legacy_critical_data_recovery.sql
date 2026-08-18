CREATE TABLE IF NOT EXISTS legacy_sqlite_recovery (
    source_path       TEXT NOT NULL,
    table_name        TEXT NOT NULL,
    source_size       BIGINT NOT NULL DEFAULT 0,
    imported_rows     BIGINT NOT NULL DEFAULT 0,
    skipped_rows      BIGINT NOT NULL DEFAULT 0,
    completed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_path, table_name)
);

CREATE INDEX IF NOT EXISTS idx_legacy_sqlite_recovery_completed
    ON legacy_sqlite_recovery(completed_at);
