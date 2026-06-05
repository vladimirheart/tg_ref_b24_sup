# 2026-06-05 22:34:44 - arch ui audit task breakdown

## Промты пользователя

- `давай теперь по задачам`

## Что изменено

- в `ai-context/tasks/task-list.md` добавлены новые детализированные задачи
  `01-128`..`01-132` как рабочее продолжение audit-эпика `01-024`;
- в `ai-context/tasks/task-details/01-024.md` добавлен блок `Порожденные задачи`,
  который связывает roadmap-аудит с отдельными исполнимыми пакетами;
- созданы новые `task-details`:
  `01-128` — settings page shell bootstrap/runtime,
  `01-129` — bounded split giant settings runtime,
  `01-130` — bounded split `dialogs.js`,
  `01-131` — transport split `ChannelApiController`,
  `01-132` — transport split `AnalyticsController`.

## Проверка

- `git diff --check -- ai-context/tasks/task-list.md ai-context/tasks/task-details/01-024.md ai-context/tasks/task-details/01-128.md ai-context/tasks/task-details/01-129.md ai-context/tasks/task-details/01-130.md ai-context/tasks/task-details/01-131.md ai-context/tasks/task-details/01-132.md`
