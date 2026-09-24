CREATE TABLE IF NOT EXISTS project_boards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope_type VARCHAR(20) NOT NULL,
    scope_key VARCHAR(320) NOT NULL UNIQUE,
    project_id BIGINT,
    owner_identity VARCHAR(255),
    name VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_project_board_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_project_boards_scope_type
    ON project_boards(scope_type);

CREATE INDEX idx_project_boards_project
    ON project_boards(project_id);

CREATE TABLE IF NOT EXISTS board_columns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id BIGINT NOT NULL,
    column_key VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_board_column_key UNIQUE(board_id, column_key),
    CONSTRAINT uq_board_column_position UNIQUE(board_id, position),
    CONSTRAINT fk_board_column_board FOREIGN KEY (board_id) REFERENCES project_boards(id) ON DELETE CASCADE
);

CREATE INDEX idx_board_columns_board_position
    ON board_columns(board_id, position, id);

CREATE TABLE IF NOT EXISTS board_task_placements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id BIGINT NOT NULL,
    column_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    position BIGINT NOT NULL DEFAULT 1000,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255),
    CONSTRAINT uq_board_task_placement UNIQUE(board_id, task_id),
    CONSTRAINT fk_board_task_placement_board FOREIGN KEY (board_id) REFERENCES project_boards(id) ON DELETE CASCADE,
    CONSTRAINT fk_board_task_placement_column FOREIGN KEY (column_id) REFERENCES board_columns(id) ON DELETE CASCADE,
    CONSTRAINT fk_board_task_placement_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_board_task_placements_column_position
    ON board_task_placements(column_id, position, id);

CREATE INDEX idx_board_task_placements_task
    ON board_task_placements(task_id);
