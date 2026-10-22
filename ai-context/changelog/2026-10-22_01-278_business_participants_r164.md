# 01-278 — BUSINESS-список участников и доставка, R164

Время: 2026-10-22 11:16:54 +03:00 (Europe/Moscow).

## Пользовательский промт

> продолжай

Значимое уточнение текущей сессии:

> репо обновлён. проверь и погнали дальше. рабочий экземпляр проекта на этой машине, поэтому docker и его контейнеры можно перезапускать.

Сохранено ограничение пользователя: не предоставлять ручные команды фиксации.

## Baseline и среда

HEAD/origin/main/actual remote main:
40e2ff6277f252e85681d94068198f85f35d4c12, parent
86bf3e41cbf0acbed5630df47eb2253d044cc5bf.
Проверены subject и 9 реальных файлов R163; main/index до работы чистые.
Detached sandbox: .tmp/01-278-r164-business-participants.
Рабочий проект находится здесь, а не на другой машине из истории R160–R162.
Docker Server 29.7.2; рабочие web/bot/worker/direct/PostgreSQL/RabbitMQ/Redis
healthy при read-only проверке. Это не functional acceptance и не подтверждение
рабочих revision/DB migrations/flags. Restart/rebuild/deploy/migration/backfill/
config/flags не выполнялись. Старый commit/push grant не перенесён из другой
сессии; staging/commit/push отсутствуют.

## Изменения

- V79 создаёт только новый BUSINESS roster и append-only историю. Полный
  frozen owner, numeric member/actor IDs и snapshots; UPDATE roster identity и
  UPDATE/DELETE истории запрещены. Нет копирования legacy ticket_participants,
  перепривязки существующих строк или изменения старых миграций.
- На странице ответственного — READ списка/истории, добавление и удаление,
  CSRF, public locator и сверка полного owner. Actor требует текущий OPERATE
  активного бизнеса и открытого обращения; member — READ, включая VIEWER и
  ALL_BUSINESSES VIEWER, уникальный enabled/unblocked non-AI account.
  Членство не выдаёт доступ. Удаление возможно после отзыва/disable/delete member.
- Независимый participant CAS по max event revision полного owner, общий
  advisory/root lock и account/business/grants locks. Final INSERT SELECT и
  DELETE повторяют owner, unique raw ID, actor authority, revision и target READ
  для ADD. Предел 200 участников; stale/ABA/concurrent/no-op не дают успех.
  Ошибка audit/outbox откатывает roster/revision/event/invalidation.
- BUSINESS notification discovery читает numeric/current full-owner roster,
  не raw legacy participants. Нет empty→all operators fallback этого entrypoint.
  Перед roster notification INSERT повторяются current READ, frozen owner и
  текущая numeric membership/name: удаление, revoke, disable, rename и reuse
  имени до INSERT не наследуют старую доставку. Новое имя — одна identity,
  а не legacy список, разделяемый пробелами/запятыми.
- Уже созданное уведомление удалением из roster не отменяется. Responsible/
  active attribution и explicit/channel recipient workflows остаются отдельными
  gates; новые проверки не объявляются полной recipient policy.
- business_participants_changed даёт metadata-only scoped invalidation через
  durable outbox, без raw recapture, bell при mutation, client/provider, AI,
  assignment или queue callbacks. Readiness проверяет обе таблицы V79.
- Карточка явно маркирует старые participants как legacy-проекцию, не новый
  список. Ссылка ведёт к управлению ответственным и участниками; JS cachebuster
  обновлён. Старые raw projection reads не классифицируются задним числом.
- Сводка задачи, ближайшая очередь и S0-контракт обновлены. Source minimum V79,
  рабочая миграция не выполнена. GENERAL roster не добавлен.

## Валидация

Совокупно 567 Java tests / 15 свежих Surefire XML GREEN, failures/errors/skips=0:
302 неизменённых regression cases из r164-regression-v3.log (весь run 533 GREEN)
и финальные 265 cases / 6 классов из r164-feature-final-v5.log, exit 0.
Compiled/source timestamps и report freshness сверены по каждому классу.

- BusinessTicketAssignmentHttpPostgresTest — 114, в том числе 51 новый case:
  реальный rendered CSRF ADD VIEWER/reload/REMOVE; независимые revisions,
  authority/forged owner/late revocation, concurrency/ABA/no-op, rollback обоих
  mutation paths, 200-bound, numeric rename с пробелом/username reuse, revoke/
  disable/delete member, отсутствие raw/global fallback, immutable history,
  generation separation, missing schema и V78→V79 без импорта старых rows.
- Семь JDBC-boundary injections перед final roster INSERT/DELETE повторяют
  owner/actor/target/collision после раннего proof. Это deterministic injection
  в test transaction, не симуляция отзыва, обходящего удерживаемый grant lock.
- UiEventOutboxFrozenDeliveryPostgresTest — 98: metadata positive/6 changed-owner
  negatives и 7 final notification INSERT cases с удалением/revoke/disable/
  rename/username reuse/сменой owner. Нет provider/AI/queue side effects.
- Readiness — 26 (два новых missing-V79 checks); NotificationService — 9,
  realtime — 6, watcher — 12.
- Regression: GENERAL HTTP 74, participant snapshots 73/legacy guard 3,
  legacy GENERAL boundary 5, raw participant writes 51, notification scope 8,
  scoped candidate pool 43, SLA writer 26/routing 19.
- node --check business-dialogs.js и 18 Node tests GREEN:
  r164-business-ui-node-v2.log. В R110 harness нормализованы CRLF/LF при чтении
  source, исходные поведенческие assertions сохранены. Первичный sandbox run
  упал только на LF anchor; тот же baseline test на main GREEN.
- Предварительные RED сохранены: r164-compile.log (missing exception import),
  r164-feature-v2.log (новый и прежний recreated fixture использовали один
  фиксированный public ID), r164-feature-final-v4.log (ambiguous Mockito/Hamcrest
  matcher в новом тесте), первый Node log (CRLF anchor). Причины исправлены;
  assignment target VIEWER по-прежнему запрещён — assertion перенесён в сам
  assignment select, а READ participant VIEWER проверяется отдельно.
- Guarded apply использует точный manifest 19 файлов, pinned baseline blobs/
  absent new targets, fresh HEAD/origin/remote и clean main/index перед записью,
  затем replay тех же apply_patch transformations после GREEN и проверку
  UTF-8/LF SHA-256/file set/diff/index. Четыре Sass CSS исключаются только по
  равенству baseline Git blobs. Logs и worktrees не удаляются.

## Осталось

GENERAL participant workflow, historical attribution legacy responsible/active/
participants и остальных child rows, оставшиеся producer/write/reply/lifecycle/
read APIs, внешние служебные чаты/destinations, global/system/task policy,
S2B решения владельца, AI privacy/media/LLM/delivery и сквозная рабочая приёмка.
V79 migration/flags/rollout/rollback — отдельный согласованный рабочий этап.
Пакет закрывает новый BUSINESS roster, а не всю 01-278; задача остаётся 🟡.

## Точный manifest (19 файлов)

- `ai-context/changelog/2026-10-22_01-278_business_participants_r164.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/BusinessTicketAssignmentController.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessTicketAssignmentService.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessTicketParticipantService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogRealtimeEventService.java`
- `spring-panel/src/main/java/com/example/panel/service/NotificationService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V79__business_ticket_participants.sql`
- `spring-panel/src/main/resources/static/js/business-dialogs.js`
- `spring-panel/src/main/resources/templates/businesses/dialog-assignment.html`
- `spring-panel/src/main/resources/templates/businesses/dialogs.html`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/BusinessTicketAssignmentHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/UiEventOutboxFrozenDeliveryPostgresTest.java`
- `spring-panel/src/test/js/business-manual-ticket-ui-r110.test.js`
- `spring-panel/src/test/js/business-ticket-workspace-ui-r105.test.js`

