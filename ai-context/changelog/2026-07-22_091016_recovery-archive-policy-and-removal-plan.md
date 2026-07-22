# 2026-07-22 09:10:16 — recovery archive policy and removal plan

## Контекст
- Пользователь: `забирай следующий шаг в работу`
- Значимый контекст из `01-150`: после разделения `dialog_config.question_templates` и bot settings следующим шагом оставалось зафиксировать финальный removal-plan по remaining migration-only helper'ам и отдельно оформить policy для historical recovery snapshots как для архивных артефактов, а не рабочего schema-contract.

## Что сделано
- В `temp-recovery/README.md` добавлена явная архивная политика:
  - `temp-recovery/` описан как каталог recovery/forensics snapshot'ов;
  - зафиксировано, что эти файлы не являются canonical fixtures и не должны использоваться как аргумент в пользу живого schema-contract;
  - отдельно объяснено, почему legacy-поля внутри snapshot'ов сохраняются намеренно.
- В `temp-recovery/routing-migration-backup-2026-07-08_085737/README.md` локально задокументирован самый важный mixed-schema snapshot:
  - указано, что `settings.json` в этой папке архивный;
  - перечислены legacy-поля, которые там остаются осознанно;
  - зафиксировано, что этот snapshot нельзя трактовать как актуальный runtime/public contract.
- В `ai-context/tasks/task-details/01-150.md` добавлен зафиксированный removal-plan:
  - перечислены remaining migration-only helpers (`question_flow`, `rating_system`, top-level/root cooldown and auto-close residues, template-level `timeout_hours` / `auto_close_hours`, legacy `questions_cfg` template selection);
  - для каждого helper зафиксированы location/status/decision;
  - отдельно выделено, что `dialog_config.question_templates` больше не относится к bot legacy mirror cleanup и должен жить как отдельный operator/workspace domain;
  - historical recovery snapshots зафиксированы как закрытая policy-часть, а не как открытый schema спор.

## Проверки
- Тесты не запускались: изменения только в документации и task governance, без runtime-кода.

## Следующий шаг
- Решить, закрывается ли `01-150` как completed audit/removal-preparation slice, или берётся ещё один implementation-cut по template-level `timeout_hours` / `auto_close_hours`.
