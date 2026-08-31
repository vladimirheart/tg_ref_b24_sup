# 2026-08-31 12:18 MSK — storage recovery rollout and follow-up tasks

## Промпты пользователя

- «давай дальше по этой-же задаче. осталось немного уже. я пытался сам частично добить одну проблему но вылезли проблемы»
- «продолжи и ты пишешь, что в minio только аватары пользователей панели, а остально это легаси. от легаси нужно избавляться и переносить в новый контур»
- «проект не перезапускал. да, пустило в админку, но 1. очень долго грузилось. 2. куда-то пропали аватары клиентов и всё медиа из диалогов. не потерялось-ли там ничего во время разрезания сервисов?»

## Что изменено

- Обновлён `ai-context/tasks/task-details/01-217.md`:
  - добавлена секция live recovery validation за 2026-08-31;
  - зафиксированы baseline-comparison evidence, восстановление `52 available / 20 missing / 1 external`, GREEN storage gates и текущий ingress-contract;
  - уточнён оставшийся объём работ после recovery rollout;
  - добавлены follow-up tasks для свежего quarantine rehearsal и предметного media/avatar audit.
- Обновлён `ai-context/tasks/task-list.md`:
  - добавлены задачи `01-220` и `01-221` со статусом `🟠`.
- Создан `ai-context/tasks/task-details/01-220.md`:
  - описан отдельный контур fresh evidence и non-mutating `-Apply -WhatIf` rehearshal на recovery HEAD.
- Создан `ai-context/tasks/task-details/01-221.md`:
  - описан отдельный аудит client avatars и dialog media после recovery storage-контура.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-217.md`
- `ai-context/tasks/task-details/01-220.md`
- `ai-context/tasks/task-details/01-221.md`
- `ai-context/changelog/2026-08-31_121800_storage-recovery-rollout-and-followups.md`
