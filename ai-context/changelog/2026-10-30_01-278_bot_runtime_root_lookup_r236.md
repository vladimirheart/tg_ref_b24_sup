# 01-278 R236 — однозначность bot-runtime root lookup

Время: 2026-10-30, 16:19 +03:00, Europe/Moscow; текущая Windows/Docker-сессия.
Промт пользователя:

> продолжай

## Изменение

Проверены свежие HEAD/origin/main/actual remote:
b0463792543f0f6f3a346205ad9116e04aab262a, parent e1b85101f, subject/все3 paths
R235. Main чистый, source spring-panel после R234 не менялся; сохранены правки
владельца. Рабочий web уже R234, повторного rollout не требовалось для baseline.

BotRuntimeTicketWriteService.loadTicket больше не выбирает произвольную строку
LIMIT1 после отдельного uniqueness guard. Загружающий SELECT читает LIMIT2,
extractor возвращает root только при ровно одной строке в этом statement.
Поздний дубль отсекается до reopen/relay/client edit/operator edit/feedback и
их downstream handoff. Сохранены ранний hasUniqueTicket и его DB-error отказ,
точный raw ID, BIGINT owner/channel, nullable projection, status и compatibility
BUSINESS/GENERAL/UNRESOLVED. Public signatures/URI/body/transport token и grants
не менялись; operatorIdentity не используется как доказанный Authentication.

## Проверки

96 новых tests на реальном PostgreSQL:3 scope ×5 операций ×4 root boundaries,
дополнительный resolver contract без предположений о порядке строк, raw padded/
case/Unicode/quote/tab, BIGINT>2^53, nullable columns, invalid ID/DB failure.
Запрещённые операции проверяют неизменность SQL rows после deliberate mutation,
отсутствие collaborator writes/outbox/ledger/workflow/feedback и pending expiry.
Положительные controls сохраняют доступность всех пяти операций.

Baseline на неизменённом product:96 tests/18 assertion failures/0 errors/skips,
78 controls GREEN. Первый infrastructure run был96 errors из-за несовместимого
Docker API старого client; не выдан за defect RED. JVM-only api.version=1.44
устранил эту ошибку, product/tests не менялись. После source fix один selected
GREEN run:157 tests/3 suites/0 failures/errors/skips —96 новых, прежние
BotRuntimeTicketWriteServiceTest и BotRuntimeWriteApiControllerWebMvcTest целиком.
Новые source/class hashes/case names после RED неизменны, старые assertions
не адаптировались. Controller filters=false проверяет token/delegation, не full
security chain. Boundary mutations scripted, не concurrent transaction proof.

Проверенный R235 JAR SHA256
2e7eef902d715d9ddcc16831acb1832d2960d704f3dd90364dc40574e3f3e0d3:
все1421 classes до fix совпали с прежним tested compile, source main unchanged.
В fresh isolated harness compiled1 main и4 test sources через Maven/JDK17;
1417 unrelated classes прежние, изменились только4 classes самого service.
Это incremental private-method verification, не полный новый build.
Helper640MiB/heap256MiB, отдельный Testcontainers PostgreSQL/fresh schemas;
рабочая datasource в тестах не использовалась. После архивации proof owned
helper удалён; Testcontainers ресурсы завершены самим reaper.

## Runtime и остаток

Все18 project containers/IDs/images/start/restarts/ENV/mounts/HostConfig прежние,
Compose/env/shared settings/DB fingerprints сохранены; единственное разрешённое
отличие input hashes — mutable backup-policy heartbeat. Flyway83/V83 прежняя.
Web R234 e1b85101f, image
sha256:2643c85d1874a0627c5211dd5e582d96aad3b684449c466bc0e47ce7512a27f3,
container807511e02a18d5cf87d552822a8e0fc27f4b02df870b3439b9307c4bda58a71a,
healthy/restart0/1 replica. Readiness200UP, fresh logs0WARN0ERROR; overall
health503DOWN только из-за прежнего inboundDLQ1. Read-only health через реальный
login, session logout выполнен. Rollback
iguana-panel:rollback-r235-web-20261030-0558a3411df9 сохранён; откат не запускался.
Migration/backfill/flags/queue/provider и worker/bot/deploy не выполнялись.

Это proof однозначности на момент SELECT, не атомарность последующей записи.
Открыты owning DML/root revalidation client edit/reopen, frozen full
business/public/kind/generation event context, pending/JPA feedback binding,
register/clear activity и остальные child/media/delivery paths. PENDING
task/project/system/page/notification policies, AI privacy/retention/delivery,
fleet/provider/flags/S6 и решение по DLQ1 прежние. Historical S2B снятR190.
Задача🟡; R236 source-only, в рабочий web ещё не включён.

Scope5:service/new SQL test/task/S0/этот append-only changelog. Изменения сначала
проверены в detached worktree, затем те же guarded changes перенесены в main;
exact scope/content/diff checks. Commit/push/deploy не выполнялись.
Evidence .tmp/01-278-r236-evidence/: frozen sources/bytecode/cases, RED/GREEN
XML/logs, JAR/class/compiler proof, runtime/DB snapshots и apply/result JSON.
