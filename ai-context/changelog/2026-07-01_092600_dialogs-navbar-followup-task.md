# 2026-07-01 09:26:00 — dialogs navbar follow-up task

## Prompt

`ты пишешь: "Отдельно оставил в описании, что старый красный DialogsControllerWebMvcTest относится к server-side navbar/template contract, а не к runtime-split страницы диалогов."`

`это-же нужно починить. создай отдельную задачу`

## Что сделано

- В `ai-context/tasks/task-list.md` добавлена отдельная follow-up задача
  `01-136` для server-side `dialogs` navbar/template contract.
- Формулировка задачи явно привязана к цели: довести `/dialogs` page-template
  contract до зелёного `DialogsControllerWebMvcTest`, не смешивая это с уже
  закрытым runtime-split `01-130`.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
