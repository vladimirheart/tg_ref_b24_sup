CREATE TABLE IF NOT EXISTS project_boards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scope_type TEXT NOT NULL,
    scope_key TEXT NOT NULL UNIQUE,
    project_id INTEGER REFERENCES projects(id) ON DELETE CASCADE,
    owner_identity TEXT,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_boards_scope_type
    ON project_boards(scope_type);

CREATE INDEX IF NOT EXISTS idx_project_boards_project
    ON project_boards(project_id);

CREATE TABLE IF NOT EXISTS board_columns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    board_id INTEGER NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    column_key TEXT NOT NULL,
    name TEXT NOT NULL,
    position INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(board_id, column_key),
    UNIQUE(board_id, position)
);

CREATE INDEX IF NOT EXISTS idx_board_columns_board_position
    ON board_columns(board_id, position, id);

CREATE TABLE IF NOT EXISTS board_task_placements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    board_id INTEGER NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    column_id INTEGER NOT NULL REFERENCES board_columns(id) ON DELETE CASCADE,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 1000,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by TEXT,
    UNIQUE(board_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_board_task_placements_column_position
    ON board_task_placements(column_id, position, id);

CREATE INDEX IF NOT EXISTS idx_board_task_placements_task
    ON board_task_placements(task_id);
