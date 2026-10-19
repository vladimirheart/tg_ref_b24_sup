# 01-278 — ручное назначение ответственного GENERAL (R139)

Дата: 2026-10-19. Задача: 01-278, статус 🟡.

Промт пользователя:

> продолжай

Сохраняется уточнение пользователя: рабочий Docker и проект находятся на другой
локальной машине и в другой сессии агента. Здесь source/test, local Docker —
только isolated PostgreSQL Testcontainers; runtime не инспектировался/не менялся.

Baseline HEAD/origin/main/actual remote main:
3f7e45d4b6fc6c16700c3bc7efc465d2aa445ee7, parent
99063c71ec59171433a58a1eca07a94f02527e43. Проверены actual 12 файлов R138.
Работа выполнена в detached worktree с чистого основного checkout.

V69 добавляет immutable GENERAL assignment events полного user/raw/public owner,
actor/assignee snapshots, work revision и отдельную assignment revision.
Manual assign/replace/remove разрешён для pending/open через PAGE_DIALOGS,
current GENERAL READ+OPERATE и CSRF; target требует current READ+OPERATE и
canonical active unique account. Назначение не выдаёт page/GENERAL access.
Legacy ticket_responsibles не читаются и не переносятся; backfill/grants нет.

Root lock сериализует CAS с другими assignments/status/owner; actor/target
account/grants удерживаются до commit. Assignment event и metadata GENERAL
outbox атомарны. Stale/ABA/no-op не создают events. Root/work revision, interval
и reply не меняются. Current/history reprove READ/owner до LIMIT; новый owner/
generation не наследует историю. Revoked target остаётся snapshot с warning.
UI: actual CSRF form, bounded candidate pool и история, escaped labels.
assignment_changed поддержан отдельными GENERAL SSE/Redis и watcher guards.
Closed ticket — assignment read-only; missing V69 блокирует readiness/write,
current/history без legacy fallback. S0 и актуальная сводка задачи обновлены.

Validation: 333/333 GREEN / 10 XML suites, failures/errors/skips=0:
assignment 48, work 85, HTTP 41, reply 66, realtime 38, reads 7, controllers 10,
readiness 10, outbox scope 16, watcher 12. Compile 682 main / 505 test.
Full Flyway до V69, upgrade V68→V69 сохраняет legacy/root/grants; concurrency,
target revocation locks, status/assignment ABA, exact scope before limit,
negative owner, rollback, immutable, actual Cookie/XOR CSRF, escaped staff,
READ-only и late-change checks; два Node harness и diff check GREEN.
XML freshness и exact-set/SHA-256 guards: 14 project files, generated CSS исключён.

Staging/commit/push оставлены пользователю. Рабочие migrations/backfill/flags,
rebuild/restart/deploy и live Redis/proxy/provider acceptance не выполнялись.
GENERAL auto-assignment/load balancing, legacy responsibility attribution,
прочие GENERAL writes/notifications и остальные блоки сводки ещё PENDING.
