# 01-278 / S2A / R51 — query API для истории диалога

Дата: 2026-10-12. Baseline: `6cd44c6dbeb02e65b82071e0d945a2e8268386b6`.

Решение: продолжить R50 после проверки 9 новых коммитов на GitHub main.

- Добавлен GET `/api/dialog-ticket/history?ticketId=...`, сохраняющий raw opaque ID.
- Реиспользован прежний `DialogReadService.loadHistory`; никаких новых auth/DB pathways.
- Polling истории переключён на query API; прежний path route остался.
- Новые WebMvc/JS tests на slash, пробелы, %, Unicode, literal null и channel ID.
- Operator: separate pure self-test, isolated detached Maven+Node validate и
  отдельно разрешаемый apply, без staging/commit/push.
- Это не browser/Tomcat E2E. Notes, workspace, actions, attachments и другие
  API ещё могут принимать ID через path; business scope этим не включается.
- Не запускались production migration/backfill, feature flag или rollout.
- S2B/R12 не утверждены; 01-278 🟡.
