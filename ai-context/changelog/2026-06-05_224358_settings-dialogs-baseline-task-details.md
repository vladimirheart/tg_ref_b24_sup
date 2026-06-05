# 2026-06-05 22:43:58 - settings dialogs baseline task details

## Промты пользователя

- `норм. дальше`

## Что изменено

- созданы missing `task-details` для связанных settings baseline-задач
  `01-111`, `01-112`, `01-113`, `01-114`, `01-115`, чтобы новые audit-задачи
  `01-128` и `01-129` ссылались уже на явные постановки, а не только на строки
  в `task-list`;
- в `ai-context/tasks/task-details/01-130.md` добавлен раздел
  `Пересечения с backlog`, который фиксирует dialog runtime baseline вокруг
  `01-090`, `01-091`, `01-101`, `01-102`, `01-103`, `01-104`, `01-109`,
  `01-110` и `01-117`;
- task-контур теперь лучше отражает, какие старые settings/dialogs UX-пакеты
  считаются compatibility baseline перед дальнейшим client-side split.

## Проверка

- `git diff --check -- ai-context/tasks/task-details/01-111.md ai-context/tasks/task-details/01-112.md ai-context/tasks/task-details/01-113.md ai-context/tasks/task-details/01-114.md ai-context/tasks/task-details/01-115.md ai-context/tasks/task-details/01-130.md`
