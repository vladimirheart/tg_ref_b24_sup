# 01-278 — R12: проект происхождения задач и связей

- Дата: 2026-10-11; revision: `01_278_S2B_R12V3_TASK_ORIGIN_DESIGN_ONLY`.
- Промпт пользователя: «допустим. только про задачи мы говорим только в рамках их создания из обращений, а задачи созданные вручную могут иметь любые сущности».
- Продолжение: пользователь прислал лог R11 с `TASK_01_278_S2B_R11_PREPARE_RESULT=GREEN` и `S2B_POLICY_STATUS=PROPOSED_NOT_APPROVED`.
- Файлы: `ai-context/tasks/task-details/01-278.md`, `ai-context/tasks/task-details/01-278-s2b-approval-input.md`, новый append-only changelog (этот файл).
- Source-аудит: ручной create в `TaskApiController`; auto-close ticket-derived в `DialogAutoCloseFollowUpTaskService`; `Task.source` — свободная строка, `task_links` — только тикеты, project membership отдельно.
- Изменение: только проектная документация. Предложены явный origin задачи, независимая область доступа, любые типизированные связи для ручных задач на объекты разных бизнесов с отдельным per-entity authorization, отсутствие догадок по двум historical unlinked tasks.
- Статус: **PROPOSED_NOT_APPROVED**. Java, SQL, Flyway, DB, runtime, private decisions R6/R9/R11, queues, flags и production не изменялись.
- Проверка: R12 JS self-test, pinned Git/blob guards, detached temporary worktree, exact changed-file set, Markdown invariants, git diff --check; Maven не запускался (только docs).
