# 01-278 R134: отдельный realtime GENERAL и сводка выполненного/остатка

Checkpoint: 2026-10-19 12:29:08 MSK, sandbox validation GREEN.

Промт пользователя: «давай дальше. и обнови документ на предмет уже выполненного и оставшегося».

Baseline HEAD/origin/main и actual remote main:
fcda0e41a03ec5c9e1574de867555c4642fd0976, parent
0312c10156eff2939299d86ee8119e15611fbab1. Проверены message и реальные
15 файлов R133; исходный основной checkout чистый. Source и проверки —
detached worktree того же baseline.

Scope — ровно 18 project files:

- `ai-context/changelog/2026-10-19_01-278_general_realtime_progress_r134.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/controller/GeneralTicketStreamController.java`
- `spring-panel/src/main/java/com/example/panel/runtime/UiEventRedisSubscriptionConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketAccessService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketEventScope.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketRealtimeService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventStreamHeartbeatScheduler.java`
- `spring-panel/src/main/resources/static/js/general-dialogs.js`
- `spring-panel/src/main/resources/templates/general/dialogs.html`
- `spring-panel/src/test/java/com/example/panel/controller/GeneralTicketControllersWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/runtime/UiEventRedisSubscriptionConfigurationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketReadServicePostgresIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketRealtimePostgresTest.java`
- `spring-panel/src/test/js/general-ticket-realtime-ui-r134.test.js`

В начале 01-278.md теперь находится текущая карта семи блоков: что реализовано
в исходниках и проверено изолированно, что ещё требует кода, решения владельца,
сквозной приёмки или отдельного выпуска. R133 AI controls/curated MEMORY/
approved handoff больше не обозначены текущим остатком. GENERAL не смешивается
с unresolved NULL; исправлена устаревшая формулировка об обязательном бизнесе
каждого нового ticket. Исторические checkpoint сохранены. Общий статус 🟡.

Новый GENERAL SSE endpoint /api/general-tickets/events/stream требует
PAGE_DIALOGS на handshake, foundation/general-ticket-queue и live GENERAL READ.
GeneralTicketEventScope отделён от legacy scope validator. Watcher использует
исходный V61 GENERAL snapshot, не захватывает новый owner по raw ID и не
вызывает legacy bell/AI/assignment/support-chat. Current tuple/public generation,
explicit GENERAL/NULL business и unique raw ID проверяются перед publish и
повторно вместе с account/grant перед каждым emitter. Старые/неполные envelopes,
scope change, owner change, collision и missing schema закрыты; cursor идёт дальше.

Redis topic <existing-ui-topic>:general:v1 отделён от legacy topic; versioned
envelope не содержит диалога/вложений. Browser invalidation содержит только
ticketPublicId/reason/time. Grant ALL/ROLE_ADMIN не даёт GENERAL READ.
Общий CURRENT_ACCOUNT ужесточает и существующие GENERAL reads: enabled,
unblocked, unique case-insensitive username. Principal whitespace не отбрасывается,
чтобы не присвоить grant другого canonical account.

UI объединяет refresh bursts, перечитывает текущие API/открытую карточку,
сохраняет фильтры и pagination, на reconnect перечитывает пропущенные изменения.
Error/revocation очищают данные и отменяют stale HTTP responses; pagehide
закрывает stream/таймер. Heartbeat заново проверяет право, при отказе отправляет
только metadata-only revocation и закрывает emitter. Redis error не открывает
local/legacy fallback. Это best-effort invalidations, не durable event replay,
доказательство доставки или право на запись.

Валидация: 229/229 GREEN / 13 свежих XML, failures/errors/skips=0:
GENERAL realtime 34, GENERAL read 7, controllers 10, Redis topic registration 2,
R133 full Spring/HTTP 17, frozen outbox 77, outbox scope 16, watcher 12,
business SSE READ 26, legacy GENERAL exclusion 13, fanout 4, topology/runtime 11.
Проверены реальные PostgreSQL owner/grant mutations, per-emitter late revoke,
generation/collision, malformed JSON/version/scope, separate Redis roundtrip,
отсутствие текста/raw IDs в browser payload и cursor progress. Full Spring/
Flyway V1–V64 и main/test compile GREEN; новых migrations/flags нет.
Два Node harness (новый realtime и R117 queue/grants), node --check и diff check
GREEN. Freshness каждого XML сверена с final production files и своей suite.

До GREEN: test compile ошибочно использовал Redis Message как concrete class,
fixture исправлен на mock интерфейса. Run v2 — 227 cases, один strict JSON
roundtrip error: computed isValid превращался в поле valid. Getter помечен
JsonIgnore; отсутствие valid проверяется тестом. Run v3 — 228 cases, одна
test-fixture error из-за недоступного construction mock-maker. Подписки проверяются
настоящим не запущенным контейнером и реальной topic mapping; dependencies/
mock-maker не менялись. Добавлена отрицательная padded-principal проверка.
Полный v4 после всех правок — 229/229 GREEN. Failed logs в ignored target.

Перед source apply обязательны fresh pinned baseline, чистый основной checkout,
exact-set и normalized UTF-8/LF SHA-256 guard. Применяется та же последовательность
apply_patch, которая проверена в sandbox; generated Sass CSS исключён.
Staging/commit/push/deployment, рабочие migrations/backfill/config/flags,
runtime на другой машине и внешние provider mutations не выполнялись.
PostgreSQL — Testcontainers; Redis network mocked, browser/live Redis/proxy/
provider E2E и mixed-fleet acceptance не заявляются.

GENERAL write/OPERATE, bell/внешняя доставка остаются PENDING. Shared-channel
selection/resume/locations, producer context/child attribution/assignment pool,
external destination grants, task/global/system policy, AI media/LLM/delivery/
privacy/auto-reply, S2B approvals и выпуск — также PENDING. Точный текущий остаток
и приоритет следующего пакета приведены в начале task detail. Rollout запрещён.
