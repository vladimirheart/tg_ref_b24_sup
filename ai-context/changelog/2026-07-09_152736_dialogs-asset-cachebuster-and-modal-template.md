# 2026-07-09 15:27:36 - dialogs-asset-cachebuster-and-modal-template

## Что сделал

- Обновил `dialogsAssetVersion` в `dialogs/index.html`, чтобы dialogs runtime перестали браться из старого браузерного кэша.
- Обновил HTML composer-блок модалки диалога, чтобы он рендерил нормальные placeholder/labels и иконки.

## Затронутые файлы

- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-149.md`

## Контекст

```text
поведение не изменилось
```

## Проверка

- `Select-String -Path 'spring-panel/src/main/resources/templates/dialogs/index.html' -Pattern \"dialogsAssetVersion='20260709-3'\"`
- В шаблоне подтверждена новая версия ассетов для dialogs runtime.

## Влияние

- Изменения ограничены server-rendered HTML шаблоном dialogs и cache-busting статических dialogs assets.
- Backend-логика отправки сообщений и логика вопросов клиента боту не менялись.
