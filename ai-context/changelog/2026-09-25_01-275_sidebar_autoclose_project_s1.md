# 01-275 S1 — sidebar density and auto-close follow-up project

Дата: 2026-09-25

## Что сделано

- Уменьшён верхний внутренний отступ sidebar header и footprint account/footer bubble.
- В настройках автозакрытия добавлен выбор проекта для follow-up задач.
- Canonical setting: `auto_close_config.follow_up_project_id`.
- Auto-close follow-up task сохраняет существующие assignee/co-executors и дополнительно получает canonical project membership, если выбран активный project.
- Missing/archived project обрабатывается fail-soft: task остаётся без project, auto-close не ломается.
- `PanelTaskService` расширен optional projectIds поверх существующего `TaskDomainFoundationService`.
- Реестр 01-274 синхронизирован с уже завершённым GREEN closeout.

## Safety

- Source-only slice.
- Нет DB migration, runtime/container/image/queue/environment mutation.
- Существующие project/task tables используются без schema changes.
- MAX avatar fix сознательно вынесен в S2, чтобы bot-runner ownership не смешивался с panel/worker slice.

## Статус

01-275 остаётся YELLOW до S2, checkpoint, production rollout и ручной приёмки.
