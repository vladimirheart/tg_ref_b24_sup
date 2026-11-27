-- Bind the existing participant roster once to exact, unambiguous account IDs.
-- Reads never resolve historical names again; account deletion clears the binding.
ALTER TABLE task_people ADD COLUMN user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

UPDATE task_people person
   SET user_id = account.id
  FROM (SELECT username, MIN(id) AS id FROM users GROUP BY username HAVING COUNT(*) = 1) account
 WHERE person.identity = account.username
   AND person.role IN ('assignee', 'co', 'watcher');

-- Earlier system writers kept the responsible person only on tasks.assignee.
-- Retain missing historical identities too, with a NULL account binding.
INSERT INTO task_people(task_id, role, identity, user_id)
SELECT task.id, 'assignee', task.assignee, account.id
  FROM tasks task
  LEFT JOIN (SELECT username, MIN(id) AS id FROM users GROUP BY username HAVING COUNT(*) = 1) account
    ON account.username = task.assignee
 WHERE task.assignee IS NOT NULL AND BTRIM(task.assignee) <> ''
   AND NOT EXISTS (SELECT 1 FROM task_people person WHERE person.task_id = task.id AND person.role = 'assignee');

CREATE INDEX task_people_account_read_idx ON task_people(user_id, task_id)
 WHERE user_id IS NOT NULL AND role IN ('assignee', 'co', 'watcher');
