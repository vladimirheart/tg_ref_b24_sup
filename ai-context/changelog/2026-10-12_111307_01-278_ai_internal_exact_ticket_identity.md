# 01-278: точный ticket ID внутри AI-контура (R32)

Дата и время: 2026-10-12 11:13:07, Europe/Moscow.

Промт пользователя: `дальше. более широким пакетом`

## Фактический пакет

- Связанный source-only пакет после R31: 13 сервисов и один новый общий
  helper, 3 новых test files и task/changelog. Исправлены 37 повторных
  ticket-scoped trim без изменений generic text normalization.
- State/control/processing, persistence/event writers и monitoring сохраняют
  точный ID до SQL. Input history, retrieval context и debug trace не читают
  trimmed sibling. Feedback/learning/review сохраняют last_ticket_id, включая
  чтение ID из memory в review-by-key; escalation и parser prompt сохраняют ID.
- Helper exactTicketIdOrNull возвращает исходный nonblank текст либо null
  для blank input. Он не является resolver/authorization и не выбирает owner.
- Message text, actor, mode, query keys, reason, policy metadata, legacy display
  aliases и existing schema fallback не менялись; constructors/wiring прежние.

## Baseline и проверки

- Fresh fetch: HEAD=origin/main=`bbc6a8ff0270023d7865f790f7559745f3228c1c`,
  parent `b5c3c5be7ef20ae3a16c7e67c34f0b207ca3a388`. R31 проверен по SHA,
  parent/message и 4 фактическим файлам; root исходно чистый. Исходные 13
  service blobs прочитаны и сохранены как content pins для guarded переноса.
- В detached worktree сначала добавлены persistence integration tests.
  Первый test-compile обнаружил 2 ошибки wildcard Map assertion typing.
  Отдельный v2 patch заменил containsEntry на сравнение того же значения:
  fixtures/assertions по идентичности не ослаблялись, production ещё не менялся.
- Baseline run после type correction и добавления flow cases: `45` tests,
  `43` assertion failures, `0` errors/skipped. После production fix эти tests
  не менялись; добавлены отдельные 15 helper cases.
- Финальный Maven wrapper run: `152/152` tests, 19 классов,
  failures/errors/skipped = 0; новые integration: `28`, flow: `17`, helper: `15`.
  Existing AI service tests, AI Ops WebMvc (`39`), retrieval, JDBC guard,
  disabled-by-default feature flags и полный PanelApplicationTests (`1/1`) GREEN.
- PostgreSQL round-trip включает actual facade/guard/state/persistence/
  monitoring и проверяет неизменность соседнего plain root при записи.
  Event writers проверены на extended и legacy schemas, global SQL NULL events
  сохранены. Missing/duplicate exact roots отклоняются до real state/event writes.
- History/review/retrieval/debug читают exact rows. Learning tests выполняют
  все 5 write branches с JDBC capture, flow проверяет ignored-input path,
  operator mapping и escalation используют mocked внешние delegates.
  Внешние LLM/notification delivery не вызывались; parser prompt change —
  text-only замена. Это не end-to-end browser/transport/manual acceptance.
- R27/R29 Node source-contracts: `18/18` GREEN. В source audit *Ai*.java
  известных trim(ticketId)/trim(last_ticket_id ResultSet) больше нет.
- Full Spring context использует existing PostgreSQL integration support:
  только Testcontainers, временные shared-config/storage directories,
  BotProcessService и taskScheduler mocked; bot autostart=false.
  Существующий Flyway chain выполнялся только в тестовой БД, не в рабочей.
- До переноса: exact changed-file set и git diff --check. Перед root apply:
  fresh fetch, exact baseline/blob guards, clean checkout и new-file conflicts.
  Те же deterministic patches переносятся после GREEN; проверяются
  exact 19-file scope и LF/CRLF-normalized совпадение root с sandbox.

## Границы

- S2A source-only checkpoint; задача 01-278 остаётся 🟡. Business filtering/
  membership/role matrix и S2B owner approval не включены. R12 provenance
  остаётся PROPOSED_NOT_APPROVED; owner не угадывается из channel/user/label.
- AI Ops frontend сохраняет отдельные ticket trim и требует следующего review.
  Исправление backend identity не объявляется полной business isolation.
- Рабочая/production БД, aliases/assignment, migration/backfill, flags, queues,
  runtime/deploy, staging, commit и push агентом не менялись/не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/AiControlledLlmService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiInputNormalizerService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiLearningService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiOpsRuntimeService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiRetrievalService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantEscalationService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantEventService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantMessageFlowService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantOperatorFeedbackService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantPersistenceService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantReviewService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantStateService.java`
- `spring-panel/src/main/java/com/example/panel/support/LegacyTicketIdSupport.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAiExactTicketIdPersistenceIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAiExactTicketIdFlowTest.java`
- `spring-panel/src/test/java/com/example/panel/support/LegacyTicketIdSupportTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_111307_01-278_ai_internal_exact_ticket_identity.md`

## Контроль переноса

- Первый root content guard остановился RED: при применении patch потерялась
  одна пустая строка в EOF AiInputNormalizerService; остальные 18 файлов
  совпадали полностью. Семантика Java не отличалась, guard не ослаблялся.
- Root-only fidelity repair получил версии v3/v4; v4 восстановил исходный
  EOF tested sandbox. Повторная сверка проверяет полное нормализованное
  содержимое всех 19 файлов, не только body без trailing whitespace.
- До повторного GREEN staging/commit/push не предлагались и не выполнялись.
