# 01-278 R237 — root/context в bot-runtime UPDATE

Время: 2026-10-30, 17:19 +03:00, Europe/Moscow; текущая Windows/Docker-сессия.
Промт пользователя:

> давай дальше

## Изменение

Свежие HEAD/origin/main/actual remote:
3e18d38389604cff6633e2564f3bdd3e35069c23, parent b04637925. Проверены subject,
все5 paths и content R236; рабочий main чистый, изменения владельца сохранены.
От trusted R235 main source отличается только BotRuntimeTicketWriteService.

Приватный root snapshot дополнен public ID/business ID/GENERAL marker.
Reopen UPDATE сравнивает весь frozen raw ID/owner/channel/public/business/GENERAL
tuple и уникальность raw ID в самом statement. Client-edit UPDATE повторяет
root tuple/count и уникальность channel/provider/client history после lookup;
сохраняет прежние child ID/owner/channel/provider/sender ограничения.
Root FOR SHARE удерживается до завершения транзакции; reopen держит собственный
row lock. При нулевом owning UPDATE нет downstream writes/handoff, ответ
MutationResult(false,true) после раннего lookup сохранён. Nullable root/markers
через IS NOT DISTINCT FROM, exact raw ID/BIGINT/status/SQL whitespace прежние.
URI/body/public API/token/grants не менялись; operatorIdentity не является
Authentication. Новых migrations/dependencies/schema/feature flags нет.

## Проверки

157 новых real PostgreSQL tests:3 scope ×2 operations ×10 root changes ×2 raw ID,
18 поздних history duplicates,6 child changes,8 status,3 nullable controls,
2 lock lifetime tests. Последние используют TransactionTemplate/второе реальное
соединение: competing root UPDATE получает55P03 до outbox/commit, а после commit
проходит. Boundary matrix scripted, не полный concurrent MVCC proof.

Frozen baseline на неизменённом compiled R236:157/109 assertion failures/
0 errors/skips,48 controls GREEN. Подготовительные harness errors/no-tests run
не считались defect RED; исправлены versioned scripts без project mutations.
GREEN346/5 suites одним selected run:157 новых,96 R236,50 прежний runtime SQL,
11 controller,32 legacy assignment;0 failures/errors/skips. Новый source/bytecode/
case names после RED неизменны, старые tests/assertions/fixtures не адаптированы.
Controller filters=false проверяет token/delegation, не full security chain.

Verified R235 JAR SHA256
2e7eef902d715d9ddcc16831acb1832d2960d704f3dd90364dc40574e3f3e0d3,
все1421 classes сверены до baseline compile. Текущий R236 service пересобран
в fresh harness и совпал с R236 archived manifest по всем1421 classes.
Incremental1 main/6 selected test files; после fix1417 unrelated classes прежние,
изменились только4 classes service. Это не полный build. Helper640MiB/heap256MiB,
JDK17/Maven/offline/API1.44, Testcontainers PostgreSQL/fresh schemas, не рабочая
datasource. После архивации owned helper удалён, reaper завершил тестовую БД.

## Runtime и остаток

Все18 рабочих containers/IDs/images/start/restarts/ENV/mounts/HostConfig и
Compose/env/shared inputs прежние; backup-policy heartbeat учтён отдельно.
Flyway/settings/accounts/authorities/roles fingerprints прежние,83 migrations/
V83 не повторялась. Web R234 e1b85101f,
image sha256:2643c85d1874a0627c5211dd5e582d96aad3b684449c466bc0e47ce7512a27f3,
container807511e02a18d5cf87d552822a8e0fc27f4b02df870b3439b9307c4bda58a71a,
healthy/restart0/1 replica. Readiness200UP, fresh logs0WARN0ERROR; health503DOWN
из-за прежнего inboundDLQ1. Реальный read-only health login завершён logout.
Rollback iguana-panel:rollback-r235-web-20261030-0558a3411df9 сохранён;
реальный rollback не запускался. Migration/backfill/flags/queue/provider/
worker/bot/infra/deploy не выполнялись.

Это owning statement tuple/count и root row-lock lifetime proof двух путей,
не serializable phantom guard или ABA/epoch protocol. Открыты frozen outbox
context (append всё ещё принимает raw ID), pending/JPA feedback binding,
relay/operator-edit/activity owning bindings, полная child/media attribution.
PENDING task/project/system/page/notification policies, AI privacy/retention/
delivery, fleet/provider/flags/S6 и решение по DLQ1 прежние. S2B снятR190;
задача🟡. R236/R237 ещё не включены в работающий web R234.

Scope5:service/new test/task/S0/этот append-only changelog. Detached sandbox,
targeted tests, deterministic replay и guarded apply, exact paths/content/diff
checks; LF/CRLF-only отличия фиксируются отдельно. Commit/push/deploy=false.
Evidence .tmp/01-278-r237-evidence/: frozen source/classes/cases, RED/GREEN XML/
logs, compiler/JAR proof, runtime/DB snapshots, template/guards/final-result JSON.
