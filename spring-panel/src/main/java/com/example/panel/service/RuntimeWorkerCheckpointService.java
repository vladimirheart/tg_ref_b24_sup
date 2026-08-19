package com.example.panel.service;

import java.util.Optional;
import java.util.function.LongSupplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeWorkerCheckpointService {

    private final JdbcTemplate jdbcTemplate;

    public RuntimeWorkerCheckpointService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public long readLongCursorOrInitialize(String workerKey, LongSupplier bootstrapSupplier) {
        Optional<Long> existing = readLongCursor(workerKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        long bootstrap = bootstrapSupplier == null ? 0L : Math.max(0L, bootstrapSupplier.getAsLong());
        saveLongCursor(workerKey, bootstrap);
        return readLongCursor(workerKey).orElse(bootstrap);
    }

    public Optional<Long> readLongCursor(String workerKey) {
        return readCursorText(workerKey).map(raw -> {
            if (!StringUtils.hasText(raw)) {
                return 0L;
            }
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ex) {
                return 0L;
            }
        });
    }

    public Optional<String> readCursorText(String workerKey) {
        if (!StringUtils.hasText(workerKey)) {
            return Optional.empty();
        }
        return Optional.ofNullable(jdbcTemplate.query(
            "SELECT cursor_text FROM runtime_worker_checkpoints WHERE worker_key = ?",
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString("cursor_text");
            },
            workerKey.trim()
        ));
    }

    public void saveLongCursor(String workerKey, long cursor) {
        saveCursor(workerKey, Long.toString(Math.max(0L, cursor)));
    }

    public void saveCursor(String workerKey, String cursorText) {
        if (!StringUtils.hasText(workerKey)) {
            return;
        }
        String normalizedWorkerKey = workerKey.trim();
        String cursorValue = cursorText == null ? null : cursorText.trim();
        int updated = jdbcTemplate.update("""
                UPDATE runtime_worker_checkpoints
                   SET cursor_text = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE worker_key = ?
                """,
            cursorValue,
            normalizedWorkerKey
        );
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO runtime_worker_checkpoints(worker_key, cursor_text, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    """,
                normalizedWorkerKey,
                cursorValue
            );
        } catch (DuplicateKeyException ignored) {
            jdbcTemplate.update("""
                    UPDATE runtime_worker_checkpoints
                       SET cursor_text = ?,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE worker_key = ?
                    """,
                cursorValue,
                normalizedWorkerKey
            );
        }
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS runtime_worker_checkpoints (
                    worker_key TEXT PRIMARY KEY,
                    cursor_text TEXT,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
