# 01-278 R119: GENERAL исключён из legacy realtime/SSE

Checkpoint: 2026-10-16 12:41:19 MSK.

Промт пользователя: «обновил репо с новыми изменениями, прошедшими чуть дальше. проанализируй о пошли дальше».

Baseline HEAD/origin/main и remote main:
`c86a99a459be6292110553268a833bd2f2e54aac`. R118 проверен по parent
`d4ad49584c8c1823e02d0fafa784cbe954f12528` и фактическим 34 файлам.
Учтены следующие за прошлым этапом R116–R118; основной checkout исходно чист.

Изменения (10 файлов):

- `DialogRealtimeEventService`, `UiEventOutboxWatcher`, `UiEventStreamService`:
  non-GENERAL guard на dispatch/producer и повторно перед локальной/Redis
  доставкой SSE. Targeted delivery и payload scope marker не обходят canonical
  owner. GENERAL не запускает side effects через realtime handlers.
- `DialogRealtimeEventServiceTest`, `UiEventOutboxWatcherTest`,
  `UiEventStreamFanoutTest`: scope-aware fixtures сохраняют прежние exact-ID,
  notification/AI и cursor assertions. Добавлена проверка продвижения cursor
  мимо всех известных GENERAL event types.
- Новый `LegacyGeneralRealtimeBoundaryPostgresIntegrationTest`: реальные SQL
  scope checks до producer/targeted/broadcast/local delivery, поздняя смена
  marker/owner, mixed collision, malformed envelopes, marker schema failure,
  сохранение системных сигналов и запрет side effects.
- S0-контракт и detail `01-278` дополнены фактическими границами и PENDING.

Валидация в detached worktree от baseline: 39/39 test cases GREEN в шести
Surefire XML reports. Main/test Java sources собраны repository Maven wrapper;
PostgreSQL — только изолированный Testcontainers. Diff check GREEN.
Перенос проверенных исходников в основной checkout — после exact file/hash
verification; Sass output не входит в source scope.

Рабочий экземпляр находится на другой машине, агент его не менял.
Staging/commit/push, deployment, рабочие migrations/backfill/config/flags
не выполнялись. Новых migrations/flags нет. Strict business-recipient scope,
GENERAL realtime/write permissions и прочие gates задачи остаются PENDING;
статус 🟡, rollout запрещён.
