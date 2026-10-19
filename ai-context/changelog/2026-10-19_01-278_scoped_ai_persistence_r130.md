# 01-278 R130: канонические AI state/control/events

Checkpoint: 2026-10-19 09:32:22 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main и remote main: c891565e4e42d1f882e3c46ddc1e38d1338dccd0,
parent 13b243b31874cefa68da6f6bd1e3b8d468c9484c. Fresh fetch, чистый основной
checkout, message и фактические 9 файлов R129 подтверждены. Изменения и
проверки выполнены в detached worktree этого baseline.

Фактический scope — 12 файлов:

- V62: отдельные scoped control/state/events, обязательный неизменяемый owner,
  ключ generation+business для control/state, immutable audit. Legacy rows
  не изменяются/не копируются, новое состояние не наследует чужой business.
- ScopedAiSql и ScopedDialogAiPersistenceService: условные записи по frozen
  owner, атомарный state+handoff event, actor READ до LIMIT и OPERATE для control.
- MessageFlow/Escalation/NotificationService: новый store перед handoff и
  повторный scoped ai_disabled guard в финальном bell INSERT. Старые raw API
  сохранены; raw retrieval/LLM/media/memory/auto-reply не открываются.
- Три test suites: 58 store cases, 53 ingress cases и V61→V62 upgrade с
  сохранением legacy rows, immutable snapshot и schema constraints.
- S0 contract и task detail отражают внутренние APIs и остающиеся gates.

Валидация: 266/266 cases GREEN в 13 свежих Surefire XML, без failures/
errors/skips; final reports новее итоговых Java/SQL. Сохранены 77 frozen outbox
cases, notification READ/GENERAL/JPA/HTTP, прежние raw AI и callback contracts.
Full Spring context, Flyway V1–V62 и compile 669 main / 495 test sources GREEN.
Exact file set и SHA-256 guard; Sass output исключён. PostgreSQL — Testcontainers,
AI/HTTP/Redis — test mocks, live provider E2E не проверялся.

Состояние и audit означают handoff request, а не доставленное уведомление.
Новых HTTP endpoints/UI controls нет: page authority/CSRF остаются отдельной
границей. Knowledge/memory/global-source policy и scoped media/LLM/client delivery
ещё PENDING. Scoped auto-reply не включён, статус задачи 🟡, rollout не разрешён.
V62 добавлена только как source migration и проверена на test DB; рабочие DB/
backfill/config/flags/deployment не выполнялись. Runtime на другой локальной
машине и staging/commit/push не затрагивались. Остальные S2B/S3–S6 gates PENDING.
