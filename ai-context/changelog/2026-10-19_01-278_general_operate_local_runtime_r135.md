# 01-278 R135: GENERAL OPERATE, triage статуса и уточнение рабочей машины

Checkpoint: 2026-10-19 13:02:25 MSK, sandbox validation GREEN.

Промт пользователя: «продолжай. уточняю: рабочиё экземпляр находится на этой машине, где запущена текущая сессия с агентом.»

Baseline HEAD/origin/main и actual remote main:
b5e61931fc22bc94553c5044a5888d7662059ec4, parent
fcda0e41a03ec5c9e1574de867555c4642fd0976. Проверены message и реальные
18 файлов R134; исходный основной checkout чистый. Реализация/проверки —
detached worktree того же baseline. Общая задача остаётся 🟡.

Scope — ровно 21 project file:

- `ai-context/changelog/2026-10-19_01-278_general_operate_local_runtime_r135.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/GeneralTicketAccessController.java`
- `spring-panel/src/main/java/com/example/panel/controller/GeneralTicketWorkController.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketAccessService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketRealtimeService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketWorkService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxAppendService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V65__general_ticket_operate.sql`
- `spring-panel/src/main/resources/static/js/general-dialogs.js`
- `spring-panel/src/main/resources/static/js/general-ticket-access.js`
- `spring-panel/src/main/resources/templates/general/access.html`
- `spring-panel/src/main/resources/templates/general/dialogs.html`
- `spring-panel/src/main/resources/templates/general/work.html`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/GeneralTicketWorkHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketRealtimePostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketWorkPostgresTest.java`
- `spring-panel/src/test/js/general-ticket-queue-ui-r117.test.js`

Добавлен явный opt-in GENERAL OPERATE, отдельный от READ, бизнес-членства,
ALL и системной роли. Выдача требует live READ и единственного enabled/unblocked
account; администрирование закрыто PAGE_SETTINGS + ROLE_ADMIN и CSRF.
READ revoke атомарно отзывает OPERATE. Возврат READ сам по себе не возвращает
write; row lock при выдаче OPERATE сериализует её с отзывом READ.
На странице прав появились отдельные кнопки READ/OPERATE.

Из GENERAL карточки открывается серверная CSRF-форма. Первый write path
ограничен pending↔open: не resolve/reopen/close, не assignment и не сообщение
клиенту. Финальный SQL сверяет frozen tuple/public generation, GENERAL/NULL
business, unique raw ID, текущие account/READ/OPERATE и revision/status CAS.
V65 поддерживает revision PostgreSQL-триггером, включая смену status/owner/scope
сторонним writer; ручной reset и устаревшая ABA-форма запрещены.

Status/revision, immutable metadata audit и V61 GENERAL outbox вставляются
одним SQL. Ошибка audit/outbox откатывает всю операцию. ID берётся из общего
timestamp/node/counter generator, а ticket_status_changed разрешён в отдельном
GENERAL realtime. Legacy validator, AI/bell/support-chat и клиентская доставка
не открываются. В форму/браузер не добавлены raw owner IDs или текст outbox.
При очистке/потере карточки ссылка на рабочую форму сбрасывается.

V65 additive: пустая general_ticket_operate_grants, general_work_revision=0,
пустой metadata audit. Не меняет существующие owner/status/public IDs,
не классифицирует legacy и не выдаёт grants. Readiness требует V65 schema.

Валидация: 137/137 GREEN в 9 свежих Surefire XML, failures/errors/skips=0:
work PostgreSQL 26, новый full Spring/HTTP 9, GENERAL read 7, realtime 35,
controllers 10, R133 AI HTTP 17, runtime readiness 5, outbox scope 16, watcher 12.
Проверены full Flyway V1–V65 и upgrade V64→V65, отсутствие выдачи прав/смены
старых ticket fields, stale/ABA, late READ/OPERATE revoke, owner/generation/
collision/account proofs, реальный concurrent grant/revoke и rollback.
Оба Node harness R117 queue/grants и R134 realtime, node --check двух
production scripts и git diff --check GREEN.

До GREEN: первый 126-case run прошёл. Расширенный 137-case run дал одну
ошибку нового теста: API CSRF отказ ожидал browser redirect вместо штатного
JSON 403. Исправлено ожидание и добавлена проверка отсутствия выдачи OPERATE.
Повторный полный 137-case run GREEN. CSRF/security/readiness не ослаблялись;
failed log остаётся в ignored target. Freshness XML сверяется с финальными
production Java/templates/JS/SQL и соответствующей suite.

Актуальная сводка семи блоков и раздел среды task detail обновлены:
рабочий экземпляр находится на текущей машине, а не на другой.
Исторические checkpoint/changelog не переписаны. Read-only проверка перед
тестами: в текущем Docker context нет запущенных контейнеров, проверенные
типичные HTTP listeners экземпляр не выявили. Это не доказывает его отсутствие;
реальный process/container/artifact, revision/DB/flags/rollback не подтверждены.
Testcontainers на той же машине не являются рабочей приёмкой.

Перед source apply — fresh pinned baseline, чистый основной checkout,
exact-set и normalized UTF-8/LF SHA-256. В основной checkout переносится
та же последовательность apply_patch после GREEN, без generated Sass CSS.
Staging/commit/push/deploy, рабочие migrations/backfill/config/flags/rebuild/
restart не выполняются. SQL запускается только на изолированном PostgreSQL
Testcontainers; live Redis/proxy/browser/provider acceptance не заявляется.

GENERAL reply/edit/delete, полный lifecycle/assignment/manual creation,
bell/внешняя доставка; shared-channel BUSINESS/GENERAL selection/snapshot/
resume/locations; producer/child/recipient isolation; global/system/task policy;
AI media/LLM/delivery/privacy/auto-reply; S2B решения владельца и сквозная
приёмка/выпуск остаются gates. Полная карта — в начале 01-278.md.
Rollout/strict scope не разрешены; существующие feature flags не включались.
