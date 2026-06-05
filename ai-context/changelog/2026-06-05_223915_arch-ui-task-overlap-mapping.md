# 2026-06-05 22:39:15 - arch ui task overlap mapping

## Промты пользователя

- `давай дальше`

## Что изменено

- в `ai-context/tasks/task-details/01-128.md`,
  `01-129.md`, `01-131.md` и `01-132.md` добавлены разделы
  `Пересечения с backlog`, чтобы новые audit-задачи не дублировали и не
  ломали уже существующий backlog по `settings/channels/locations/dashboard`;
- в `ai-context/tasks/task-details/01-024.md` добавлен общий блок
  `Пересечения с существующим backlog`, который фиксирует, какие старые задачи
  считаются compatibility baseline для `Track A` и `Track C`;
- в task-контуре зафиксировано, что пакеты `01-071`..`01-077`,
  `01-111`..`01-115`, `01-121` и `01-118`..`01-127` нужно сохранять как
  действующий baseline при следующих рефакторинговых проходах.

## Проверка

- `git diff --check -- ai-context/tasks/task-details/01-024.md ai-context/tasks/task-details/01-128.md ai-context/tasks/task-details/01-129.md ai-context/tasks/task-details/01-131.md ai-context/tasks/task-details/01-132.md`
