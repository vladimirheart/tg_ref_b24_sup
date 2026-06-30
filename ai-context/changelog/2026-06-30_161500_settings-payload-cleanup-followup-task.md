# 2026-06-30 16:15:00 — settings payload cleanup follow-up task

## user prompt

> вынеси это лучше в отдельную задачу, а текущую будем считать выполненной

## what changed

- в `ai-context/tasks/task-list.md` задача `01-129` переведена в статус
  `🟣 ожидает ручной проверки`, а новый follow-up scope вынесен в отдельную
  задачу `01-135`;
- в `ai-context/tasks/task-details/01-129.md` зафиксировано, что основной
  client-side runtime split завершён, а remaining server-rendered payload/data
  cleanup больше не считается частью обязательного scope этой карточки;
- создана новая карточка `ai-context/tasks/task-details/01-135.md` для
  отдельного cleanup `settingsPageInitPayload` и server-rendered data contract
  в `settings/index.html`.

## verification

- `rg -n "01-129|01-135" ai-context/tasks/task-list.md ai-context/tasks/task-details -S`
