# 01-278 R238 — frozen bot outbox и feedback current-root lock

Время: 2026-10-30, 18:02 +03:00, Europe/Moscow; текущая Windows/Docker-сессия.
Промт пользователя:

> делай

## Изменение

HEAD/origin/main/actual remote 708352ed41d201ea3063cfd2c95971cb5041b3af,
parent3e18d3838. Проверены subject/все5 paths/content R237, main чистый;
изменения владельца сохранены. Runtime web R234 не требовал повторного deploy
для этой source/test фазы.

Четыре bot-runtime outbox callers передают TicketContext из исходного snapshot:
raw ID/owner/channel/public ID/business ID/nullable GENERAL. Новый INSERT
пишет captured scope-поля и повторяет exact tuple/count proof в самом SQL;
поздний перенос root больше не переопределяет получателя/бизнес события.
При изменившемся root event не пишется, primary mutation автоматически не
откатывается одним таким отказом. Raw append/overloads других producers прежние;
NULL-marker suppression/nullable projection/raw/BIGINT/text/rating/temporal
semantics сохранены. TicketContext не является recipient authorization;
UiEventTicketScope/GENERAL validators, URI/body/token/grants не менялись.

Feedback после чтения existing rating сверяет frozen root tuple/count через
SELECT FOR SHARE до изменения entities/save. При отказе false,false, нулевые
rating/pending expiry/outbox writes. Root row lock транзакции удерживается
через rating save/outbox/request save до commit. Прежние validations/expiry/
existing identity/catch pending-save failure сохранены. Pending/public/business
generation provenance и atomic claim/expiry не реализованы этим guard.

## Проверки

332 новых tests:216 real outbox INSERT boundaries (3 scope/4 operations/
9 root changes/2 raw IDs),108 feedback read-to-save boundaries (new/existing),
6 two-connection root-lock checks и2 transaction SQL rollback cases.
Frozen compiled R237 baseline332/246 assertion failures/0 errors/skips,
86 controls GREEN. GREEN792/9 suites одним selected run,0 failures/errors/skips:
332 новых,157 owning-write,96 root-lookup,50 runtime SQL,11 controller,
32 legacy assignment,2 raw-appender unit,96 append snapshot,16 scope/watch.
Новый source/7 class hashes/case names после RED не менялись. Две старые
fixtures адаптированы только к typed context handoff;66 assertion lines прежние,
все прежние case names/проверки данных и lock callbacks сохранены и выполнены.

Lock tests: реальный TransactionTemplate/независимое соединение получает55P03
при root UPDATE в точках rating/request save, после commit UPDATE проходит.
Остальная root mutation matrix scripted, не полный concurrent MVCC proof.
JPA repositories в новом наборе mocked: это не Hibernate flush/pending-row
atomicity acceptance. Controller filters=false: token/delegation, не security
chain. Старые83 migrations применялись только в isolated scope/watch test schema.

Trusted R235 JAR SHA256
2e7eef902d715d9ddcc16831acb1832d2960d704f3dd90364dc40574e3f3e0d3,
R237 baseline1421 class hashes совпали после incremental baseline compile.
Green1422 classes:1416 unrelated прежние,5 changed+1 new nested context.
Compiled2 main/10 selected test sources, не полный build. Owned640MiB helper,
JDK17/Maven/offline/API1.44/Testcontainers/fresh PostgreSQL schemas, не рабочая
datasource; helper удалён после архивации evidence, test DB завершена reaper.

## Runtime и остаток

Все18 working containers/IDs/images/start/restarts/ENV/mounts/HostConfig и
Compose/env/shared inputs прежние; mutable backup heartbeat отдельно.
Flyway/settings/accounts/authorities/roles fingerprints прежние,83 working
migrations/V83 не повторялась. Web R234 e1b85101f,
image sha256:2643c85d1874a0627c5211dd5e582d96aad3b684449c466bc0e47ce7512a27f3,
container807511e02a18d5cf87d552822a8e0fc27f4b02df870b3439b9307c4bda58a71a,
healthy/restart0/1 replica/readiness200UP, fresh0WARN0ERROR. Overall health503DOWN
из-за прежнего inboundDLQ1; read-only login завершён logout. Rollback
iguana-panel:rollback-r235-web-20261030-0558a3411df9 сохранён, реальный откат не
запускался. Working migration/backfill/flags/queue/provider/worker/bot/infra/
deploy не выполнялись.

Открыты pending-request provenance/public/business generation и atomic claim/
expiry, existing-rating owning persistence, relay/operator-edit/activity,
other raw event producers и child attribution. Full-tuple ABA/phantom/epoch,
PENDING task/project/system/page/notification policies, AI privacy/retention/
delivery, fleet/provider/flags/S6 и DLQ1 gate прежние. S2B снятR190, задача🟡.
R236–R238 ещё не включены в работающий web R234.

Scope8:2 main,2 adapted fixtures,1 new SQL test,task/S0/этот append-only changelog.
Detached sandbox/targeted tests/replay/guarded apply, exact scope/content/diff
checks; LF/CRLF-only отличия фиксируются отдельно. Commit/push/deploy=false.
Evidence .tmp/01-278-r238-evidence/: frozen source/classes/cases, RED/GREEN XML/
logs, compiler/JAR proof, runtime/DB snapshots, template/guards/final JSON.
