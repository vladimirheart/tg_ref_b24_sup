# 01-212 — production backup/recovery Phase A

- Время: 2026-08-26 16:03:06 +03:00
- Задача: 01-212
- Области: Docker backup overlay/jobs, restore rehearsal, backup monitoring evidence integration, helpers, tests, runbook

## Промт пользователя

всё зелёное. репо пушнул. проверяй и давай дальше

## Что сделано

- начата реализация следующего шага roadmap после observability;
- PostgreSQL/MinIO backup jobs отделены от panel-web/ops-worker runtime;
- off-host destination enforced by production helpers;
- restore rehearsal выполняется только в ephemeral tmpfs targets;
- 01-198 автоматически получает success/failure restore evidence из companion files;
- добавлены source-contract/unit tests и operational runbook;
- зарегистрирована 01-213 на реальную внешнюю доставку Alertmanager через существующий Iguana incident/notification boundary.

01-212 остаётся YELLOW до реального Docker smoke и off-host DR proof.
Commit/push этим apply-скриптом не выполняются.
