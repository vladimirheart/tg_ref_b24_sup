# 01-278 R123: business READ получателя SSE

Checkpoint: 2026-10-16 16:02:11 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main: b3b1c61fb468fa7bf1983f12bc04a6db64baebd0.
Проверены R122, parent 85043d072d7a87ac2e13b30e408d63c87f2e3382,
сообщение и 10 файлов предыдущего commit. Основной checkout исходно чистый;
изменения проверены в detached worktree.

Фактический scope — 8 файлов:

- UiEventTicketAccessService: текущие recipient business READ, unique exact
  ticket owner и non-GENERAL marker в одном SQL snapshot. Повторно используется
  NotificationBusinessAccessSql без изменения policy R122. Отсутствующая access
  schema закрывает отправку. Default UNRESOLVED совместим, strict-read скрывает.
- UiEventStreamService: READ перед каждой ticket-bound отправкой каждому
  emitter; одинаковый guard для local fallback и targeted/broadcast fanout.
  Отзыв grants действует на открытом соединении. Identity — Locale.ROOT.
- UiEventBusinessAccessPostgresIntegrationTest: 26 новых реальных PostgreSQL
  cases для A/B, live grants/account/business lifecycle, ADMIN denial, future/
  all scope, двух соединений, exact/ambiguous owner, GENERAL и strict scope.
- UiEventStreamFanoutTest и LegacyGeneralRealtimeBoundaryPostgresIntegrationTest:
  обновлён constructor fixture, прежние producer/marker/identity assertions
  сохранены; recipient READ изолирован mock только в старой marker suite.
- S0 contract и detail задачи описывают delivery policy и оставшиеся gates.

Валидация: 64/64 cases в 6 suites GREEN по Surefire XML.
Maven wrapper main/test compilation (665 main и 486 test sources) и diff check GREEN.
Проверены local delivery, queued envelopes и publisher unit regressions;
live Redis, browser SSE и provider E2E не выполнялись. Перенос только этих
8 файлов после exact-set/SHA-256 проверки, Sass output не входит в scope.

Новых migrations/flags нет. Рабочий экземпляр находится на другой локальной
машине и не затрагивался. Рабочие DB migrations/backfill/config/flags,
deployment, staging/commit/push не выполнялись; локальная PostgreSQL —
изолированная инфраструктура Testcontainers.

Этот этап проверяет current canonical owner и business READ. Frozen scope
queued event/outbox, page/system/task policy, общие write/realtime,
shared-channel UX, graceful resume, provider E2E и остальные S2B/S3–S6 gates
ещё PENDING. Следующий участок — canonical event scope против устаревшего
payload после смены/recreation owner. Статус 🟡, strict rollout не разрешён.
