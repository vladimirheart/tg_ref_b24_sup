# 01-278 R136: текстовый ответ в GENERAL и защита повторной отправки

Checkpoint: 2026-10-19 13:45:04 MSK, sandbox validation GREEN.

Промт пользователя: «docker запустил. продолжай с этим расчётом».
Ранее подтверждённое уточнение размещения: «продолжай. уточняю: рабочиё экземпляр находится на этой машине, где запущена текущая сессия с агентом.»

Baseline HEAD/origin/main и actual remote main:
ce42d6125dc2d9cb6724715dbb2aa904a667ab7e, parent
b5e61931fc22bc94553c5044a5888d7662059ec4. Проверены message и реальные
21 файл R135. Основной checkout чистый; source/валидация — detached worktree
того же baseline. Общая задача 🟡, не закрыта.

Scope — ровно 16 project files:

- `ai-context/changelog/2026-10-19_01-278_general_text_replies_r136.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/GeneralTicketWorkController.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTransportService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketRealtimeService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketReplyService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V66__general_ticket_text_replies.sql`
- `spring-panel/src/main/resources/templates/general/work.html`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/GeneralTicketWorkHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyTransportServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketRealtimePostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketReplyPostgresTest.java`

Добавлены GENERAL text reply service, серверная CSRF-форма и последние
20 отправок с честными состояниями. Нужны PAGE_DIALOGS + current READ/OPERATE,
explicit GENERAL/NULL business, exact tuple/public generation, unique raw ID,
canonical account enabled/unblocked, pending/open и revision. Текст ограничен
4000 UTF-16 units, blank/control/malformed Unicode отклоняются, HTML экранируется.

Форма хранит SHA-256 исходного destination: изменение канала, platform,
token, platform config, delivery settings или credential ID до POST требует
перечитать форму. После подготовки сохраняются frozen owner/actor/revision/
destination/text и уникальный request key. Под тем же key нельзя изменить
payload/actor/public owner; конкурентный POST получает один durable claim.

QUEUED→SENDING фиксируется отдельным commit до сети. После claim права,
root message/channel, revision и destination доказываются заново; row locks
удерживаются до результата. Во всех трёх текстовых provider transports guard
вызывается после network setup/serialization непосредственно перед HTTP send.
Для network setup используется копия Channel, а request строится из исходного
snapshot. Guarded path отклоняет unknown/null/noncanonical platform; прежний
legacy sendText сохраняет свой compatibility fallback. GENERAL service
не вызывает legacy reply/responsibility/AI/feedback orchestration.

При provider success одной транзакцией фиксируются SENT, canonical history
с public generation, metadata audit и V61 GENERAL operator_reply_changed outbox.
В metadata/SSE нет текста/credentials; очередь не хранит token.
Отмена прав/scope/destination → CANCELLED; неясный provider outcome → UNKNOWN.
Если провайдер уже мог принять сообщение, а запись результата не удалась,
остаётся SENDING/UNKNOWN. Повтор не отправляет его снова, UI просит ручную
сверку. Это at-most-one local attempt, не обещание exactly-once внешней доставки.
Фонового sender/replay/retry нет; queued после crash и ambiguous states
требуют отдельной reconciliation policy.

V66 additive: пустая general_ticket_text_replies, metadata-only immutable
general_ticket_reply_events, nullable history public ID. Старые owner/status/
IDs/history не меняются, grants/backfill/guess attribution не создаются.
Readiness требует новую schema. GENERAL reads отбрасывают known wrong history
generation; NULL legacy history не классифицируется и остаётся отдельным gate.

Валидация: 232/232 GREEN / 11 свежих XML, failures/errors/skips=0:
reply PostgreSQL 64, work HTTP 19, provider transport 19, work PostgreSQL 26,
GENERAL read 7, realtime 36, controllers 10, R133 AI HTTP 17, readiness 6,
outbox scope 16, watcher 12. Full Flyway V1–V66, upgrade V65→V66, отсутствие
grants/старых owner/history changes, concurrent duplicate, actor/public key
reuse, late revoke, token/platform/channel rebind, actual last-send callback,
stale destination form, real Cookie/XOR CSRF, XSS escape, ambiguous provider
outcome и DB failure после provider acceptance проверены.
Два Node harness R117/R134, node --check production scripts и diff check GREEN.
Freshness XML/compiled classes сверяется с final production и собственными tests.

До GREEN: v1 214 cases — 1 failure/9 errors новых fixtures: restubbing вызывал
предыдущий Mockito Answer с null guard; fixture пытался UPDATE immutable public
ID; shared schema дала повторный empty token; MockMvc param добавлял значение,
а не заменял его. Исправлены только fixtures, production guards не ослаблялись.
Повторный run GREEN. Затем review добавил защиту destination исходной формы
и дополнительные cross-actor/rebind cases. Финальный полный v3 — 232 GREEN.
Failed logs в ignored target, generated Sass CSS исключён из source package.

Read-only runtime checkpoint: Docker Server 29.7.2, healthy существующие
panel-web/bot-runner/ops-worker/PostgreSQL (также Redis/RabbitMQ).
Три приложения используют iguana-panel:sidebar-avatar-r120-da9694040092,
image ID sha256:9457b17ffac2692a3d03049f9f04d1942e692a16b92de0219428065018c4212c,
OCI revision da969404009276263c9b51bc362ce233ea6a7cf0 — не текущий source main.
Рабочая DB schema/flags/rollback readiness не проверялись. Сводка задачи и раздел
среды обновлены; исторические checkpoint/changelog не переписаны.

Перед source apply — fresh pinned baseline/clean checkout, exact-set и
normalized UTF-8/LF SHA-256. Тот же набор apply_patch переносится только после
GREEN. Staging/commit/push/deploy, рабочие migrations/backfill/config/flags/
rebuild/restart не выполняются. PostgreSQL — отдельный Testcontainers, не
рабочий Compose PostgreSQL. Provider network mocked; реальные сообщения
клиентам не отправляются. Рабочая/live Redis/proxy/provider приёмка не заявляется.

Остаются GENERAL media/edit/delete/lifecycle/assignment/manual creation,
reconciliation/retention, bell/служебная доставка; shared-channel UX/session/
template/location; producer/child/recipient isolation; global/system/task policy;
AI media/LLM/delivery/privacy/auto-reply; S2B решения владельца и выпуск.
Актуальная карта — в начале task detail. Rollout/strict scope не разрешены.
