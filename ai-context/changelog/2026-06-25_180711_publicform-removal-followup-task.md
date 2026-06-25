# 2026-06-25 18:07:11 - publicform removal follow-up task

## Промпты пользователя

- `ты писал:
"Отдельно проверил шире по репозиторию: вне settings ещё живёт не “хвост”, а целый backend/test слой PublicForm* — как минимум OperatorNotificationWatcher, SupportPanelIntegrationTests и связанные PublicForm модели/сервисы. Я его в этом пакете не трогал, потому что это уже отдельный removal-пакет, а не локальный cleanup шаблона настроек."
сделай отдельный таск на это`

## Что изменено

- в `ai-context/tasks/task-list.md` добавлена новая backlog-задача
  `01-134` про отдельный removal-пакет для оставшегося backend/test слоя
  `PublicForm`;
- создан detail-файл `ai-context/tasks/task-details/01-134.md`, где
  зафиксированы цель, scope, контекст и стартовый inventory известных
  `PublicForm`-точек: `OperatorNotificationWatcher`,
  `OperatorNotificationWatcherTest`, `SupportPanelIntegrationTests` и
  `PublicShellTemplateContractTest`;
- в новой задаче отдельно отражено, что это уже не cleanup settings runtime из
  `01-129`, а самостоятельный пакет удаления удалённой продуктовой
  функциональности.

## Проверка

- `Get-Content -Encoding UTF8 ai-context/tasks/task-list.md`
- `Get-Content -Encoding UTF8 ai-context/tasks/task-details/01-134.md`
- `git diff --check -- ai-context/tasks/task-list.md ai-context/tasks/task-details/01-134.md ai-context/changelog/2026-06-25_180711_publicform-removal-followup-task.md`
