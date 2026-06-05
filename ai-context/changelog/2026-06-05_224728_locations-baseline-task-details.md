# 2026-06-05 22:47:28 - locations baseline task details

## Промпты пользователя

- `дальше`

## Что изменено

- созданы missing `task-details` для baseline-пакета `01-071`..`01-077`, чтобы ветка
  `locations/iikoServer` из audit-задач `01-024`, `01-128` и `01-129` ссылалась уже на явные
  постановки, а не только на строки в `task-list`;
- в новых карточках зафиксированы bounded-роли задач:
  source switch на `iikoServer API`, отдельный settings editor для source settings,
  защита от дублей `city/value`, ручной sync с progress/schedule, auth fix, явный
  `SHA-1` credential contract и lifecycle закрытых локаций;
- в каждую карточку добавлены пересечения со смежным backlog и реальные code-touchpoints вокруг
  `settings/index.html`, `SettingsLocationsSyncController`,
  `IikoDepartmentLocationCatalogService`, `IikoDepartmentLocationsSyncService`,
  `LocationsIikoServerSourceSettingsService` и `SettingsParameterService`.

## Проверка

- `git diff --check -- ai-context/tasks/task-details/01-071.md ai-context/tasks/task-details/01-072.md ai-context/tasks/task-details/01-073.md ai-context/tasks/task-details/01-074.md ai-context/tasks/task-details/01-075.md ai-context/tasks/task-details/01-076.md ai-context/tasks/task-details/01-077.md ai-context/changelog/2026-06-05_224728_locations-baseline-task-details.md`
