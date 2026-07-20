# Изменение: отдельная задача на аудит и выпиливание legacy settings compatibility-слоя

## Промпт пользователя

> ты пишешь "UI и backend действительно читают смесь старых и новых полей." проведи аудит старого и составь план по его выпиливанию, создав отдельную задачу

## Что сделано

- В `ai-context/tasks/task-list.md` добавлена новая задача `01-150` со статусом `🟠`:
  - `Провести аудит legacy compatibility-слоя настроек бота/диалогов и спланировать его выпиливание`.

- Создан detail-файл `ai-context/tasks/task-details/01-150.md`, в котором зафиксированы:
  - аудит legacy/duplicate полей в `bot_settings`, `auto_close_config`, `dialog_config` и `channels.questions_cfg`;
  - разделение canonical, derived и deprecated частей конфигурации;
  - поэтапный план выпиливания compatibility-слоя;
  - риски, ограничения и критерии готовности;
  - связанные файлы для follow-up реализации.

## Затронутые области

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-150.md`

## Что проверить руками

- В `ai-context/tasks/task-list.md` есть строка `🟠 [01-150] ...`.
- В `ai-context/tasks/task-details/01-150.md` описаны audit findings и phased migration plan по legacy settings model.
