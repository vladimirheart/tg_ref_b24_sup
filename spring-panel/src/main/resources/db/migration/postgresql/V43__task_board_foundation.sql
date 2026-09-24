CREATE TABLE IF NOT EXISTS project_boards (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type VARCHAR(20) NOT NULL,
    scope_key VARCHAR(320) NOT NULL UNIQUE,
    project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    owner_identity VARCHAR(255),
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_boards_scope_type
    ON project_boards(scope_type);

CREATE INDEX IF NOT EXISTS idx_project_boards_project
    ON project_boards(project_id);

CREATE TABLE IF NOT EXISTS board_columns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_id BIGINT NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    column_key VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_board_column_key UNIQUE(board_id, column_key),
    CONSTRAINT uq_board_column_position UNIQUE(board_id, position)
);

CREATE INDEX IF NOT EXISTS idx_board_columns_board_position
    ON board_columns(board_id, position, id);

CREATE TABLE IF NOT EXISTS board_task_placements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_id BIGINT NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    column_id BIGINT NOT NULL REFERENCES board_columns(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    position BIGINT NOT NULL DEFAULT 1000,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT uq_board_task_placement UNIQUE(board_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_board_task_placements_column_position
    ON board_task_placements(column_id, position, id);

CREATE INDEX IF NOT EXISTS idx_board_task_placements_task
    ON board_task_placements(task_id);
