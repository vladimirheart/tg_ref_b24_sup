# 01-278 R124: frozen owner ticket-событий SSE fanout

Checkpoint: 2026-10-16 16:29:26 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main: e365ebe81505cb5f464731a6fcd5bae8813c731e.
Проверены R123, parent b3b1c61fb468fa7bf1983f12bc04a6db64baebd0,
сообщение и 8 файлов предыдущего commit. Основной checkout исходно чистый;
изменения проверены в detached worktree.

Фактический scope — 12 файлов:

- UiEventTicketScope: immutable record canonical tuple/public ID/scope/business.
- UiEventTicketAccessService: SQL capture уникального non-GENERAL owner до
  публикации и проверка frozen owner вместе с текущим recipient READ перед
  отправкой. Неизменённый business predicate R122 используется повторно.
- UiEventStreamService: один и тот же snapshot для Redis и local fallback;
  ticket envelope без proof или с несовпавшим payload.ticketId отклоняется.
- UiEventFanoutPublisher и UiEventFanoutEnvelope: отдельное internal поле
  ticketScope; compatibility overload/constructor для системных событий.
  Browser SSE names/payload сохранены, owner fields наружу не добавляются.
- UiEventTicketScopePostgresIntegrationTest: 36 новых PostgreSQL/codec/consumer
  cases. UiEventBusinessAccessPostgresIntegrationTest, UiEventStreamFanoutTest
  и LegacyGeneralRealtimeBoundaryPostgresIntegrationTest получили typed scope
  и locator fixtures; прежние business/marker/identity assertions сохранены.
- S0 contract и detail задачи описывают policy, locator prerequisite и
  остающиеся persistent outbox/mixed-version gates.

Валидация: 100/100 cases в 7 suites GREEN по Surefire XML.
Maven wrapper main/test compilation (666 main и 487 test sources), diff check и
exact changed-file set GREEN. PostgreSQL — только Testcontainers; JSON
publisher/subscriber проверены с mock Redis backend. Live Redis, browser SSE
и provider E2E не выполнялись. Generated Sass output не входит в пакет.

NULL/invalid/missing public locator теперь закрывает ticket SSE и в default
compatibility: composite tuple не доказывает generation. Backfill не добавлен
и не запускался. Новых migrations/flags нет. Рабочий экземпляр на другой
локальной машине не затрагивался. Рабочие DB/config/flags, deployment,
staging/commit/push не выполнялись.

Этот этап фиксирует owner перед transport fanout. Persistent ui_event_outbox
пока без frozen scope на исходном INSERT; сквозная проверка его callbacks и
notification effects — следующий участок. Mixed-version fleet со старыми
consumers не считается проверенным. Global page/system/task policy, общие
write/realtime, shared-channel UX, graceful resume, provider E2E и оставшиеся
S2B/S3–S6 PENDING. Статус 🟡, strict scope/rollout не разрешены.
