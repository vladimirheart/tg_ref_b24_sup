-- R301: independent shared work access; no business grants, seeds or legacy classification.
CREATE TABLE shared_work_grants (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE CHECK (user_id > 0),
    can_read BOOLEAN NOT NULL DEFAULT FALSE,
    can_create_task BOOLEAN NOT NULL DEFAULT FALSE,
    can_create_project BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (can_read OR (NOT can_create_task AND NOT can_create_project))
);
ALTER TABLE tasks ADD COLUMN shared_owner_user_id BIGINT REFERENCES users(id) CHECK (shared_owner_user_id > 0);
ALTER TABLE projects ADD COLUMN shared_owner_user_id BIGINT REFERENCES users(id) CHECK (shared_owner_user_id > 0);

CREATE TABLE shared_task_members (
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE CHECK (task_id > 0),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE CHECK (user_id > 0),
    access_role TEXT NOT NULL CHECK (access_role IN ('OWNER','EDITOR','VIEWER')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id,user_id)
);
CREATE TABLE shared_project_members (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE CHECK (project_id > 0),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE CHECK (user_id > 0),
    access_role TEXT NOT NULL CHECK (access_role IN ('OWNER','EDITOR','VIEWER')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id,user_id)
);
CREATE UNIQUE INDEX shared_task_one_owner ON shared_task_members(task_id) WHERE access_role='OWNER';
CREATE UNIQUE INDEX shared_project_one_owner ON shared_project_members(project_id) WHERE access_role='OWNER';
CREATE INDEX shared_task_member_lookup ON shared_task_members(user_id,task_id);
CREATE INDEX shared_project_member_lookup ON shared_project_members(user_id,project_id);
REVOKE ALL ON shared_work_grants, shared_task_members, shared_project_members FROM PUBLIC;
