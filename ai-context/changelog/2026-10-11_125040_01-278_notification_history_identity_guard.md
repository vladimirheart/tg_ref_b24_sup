# 01-278: идентичность обращения в уведомлениях, аудите и истории (R17)

Дата и время: 2026-10-11 12:50:40, Europe/Moscow.

Промт пользователя: `класс, давай дальше`

## Фактические изменения

- `NotificationService` использует общий `LegacyTicketIdJdbcGuard` до
  запросов получателей диалога и до fallback на всех операторов.
  Отсутствующий, пустой или неоднозначный ID не приводит к раскрытию
  получателей, сохранению уведомления или SSE refresh. Для unique ticket
  без участников прежний общий fallback сохранён; нетикетные уведомления
  и paging не переводятся на ticket guard.
- `DialogAuditService` проверяет unique root до записи action audit,
  поиска успешного действия и ticket-scoped workspace telemetry. Для
  глобальной telemetry без ticket scope сохранена запись с NULL ticket ID.
- `DialogConversationReadService` определяет пользователя предыдущей
  истории по canonical current ticket, а не по последнему messages row.
  JOIN проверяет принадлежность сообщения пользователю root; ambiguous,
  orphan и mismatched candidates исключаются до grouping и pagination,
  поэтому их problem/status/channel metadata тоже не попадают в ответ.
  Business owner и исторические channel mismatch здесь не определяются.
- Убрана обрезка точного legacy ID в composite locator, notification
  URL/lookup, audit lookup/telemetry, previous-history lookup/result и
  attachment URL/descriptor. Разные ключи ` T-EXACT ` и `T-EXACT`
  не объединяются. Нормализация username, текста, action и имени вложения,
  а также существующий способ кодирования notification URL сохранены.
- `UiEventOutboxWatcher` проверяет unique ID до initial-client-message
  lookup и realtime dispatch; запросы initial ownership получают точный ID.
  Отдельно устранён обнаруженный регрессионным тестом пропуск первой строки:
  void lambda выбирала RowCallbackHandler, который уже позиционировал
  ResultSet на строке, а внутренний next() пропускал её. Явный
  `ResultSetExtractor<Void>` обрабатывает и единственное первое событие.
  Недопустимое или failed best-effort событие не блокирует дальнейшую
  обработку и shared checkpoint; lease, cleanup и retry policy не менялись.

## Проверки и baseline

- Свежий `HEAD=origin/main=5b708ca2ebefd0248de1efcbd78972c0b59c926a`,
  parent `fe45deab061fd31eb6871bf39ea29d72f4a74408`; root checkout чистый
  до применения. Remote main повторно проверен через fetch перед переносом.
- Предыдущий commit проверен по SHA, parent, message и фактическим файлам:
  его message повторяет R15, но 17 изменённых файлов и changelog содержат
  R16. История Git не переписывалась; продолжение основано на source R16.
- Все правки сначала применены в detached temporary worktree от baseline.
  Финальный запуск repository Maven wrapper: `80/80` tests, 14 классов,
  failures/errors/skipped = 0. Из них `55` tests находятся в семи
  затронутых test files; остальные проверяют shared guard, realtime,
  outbox append, fanout, operator watcher и runtime lifecycle contracts.
- Отдельный `PanelApplicationTests`: `1/1`, полный Spring context и
  существующая Flyway-цепочка V1–V52 только в тестовом PostgreSQL контейнере.
- До GREEN исправлена ошибка компиляции нового attachment regression test
  (checked IOException), затем исправлен настоящий дефект watcher.
  Assertions и существующие integration/source-contract checks не ослаблены.
  Logged exception теста best-effort dispatch является ожидаемым сценарием.
- `git diff --check` проверен в sandbox. Перенос выполняется теми же
  deterministic apply_patch patches после проверки exact changed-file set,
  с последующей LF/CRLF-normalized сверкой содержимого основного checkout.
  Generated CSS не включён: после Maven semantic diff отсутствует.

## Границы и следующий checkpoint

- Это source-only S2A hardening, не завершение всей 01-278: статус 🟡.
  Фактический scope: 5 production services, 7 test files, task details
  и этот новый changelog — всего 14 project files.
- Рабочая/production БД, migration/backfill вне тестовых schemas, flags,
  deploy, staging, commit и push агентом не выполнялись.
- Strict business scope и canonical assignment не включены; owner не
  вычисляется из user/channel/name. Исторические channel mismatch не
  изменены и не классифицированы.
- R12 task provenance остаётся `PROPOSED_NOT_APPROVED`; S2B approvals,
  aliases, business role matrix, shared-channel decisions и S2B–S6
  остаются за границей пакета. Следующий шаг требует свежей проверки
  main/user edits; `DialogMacroService` и telemetry ticket-list lookup
  остаются кандидатами для отдельного source-only exact-ID review.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/NotificationService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAuditService.java`
- `spring-panel/src/main/java/com/example/panel/service/TicketLocatorService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/NotificationServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/NotificationPagingServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAuditServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogConversationReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/NotificationLegacyTicketGuardTest.java`
- `spring-panel/src/test/java/com/example/panel/service/TicketLocatorServicePostgresIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/UiEventOutboxWatcherTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-11_125040_01-278_notification_history_identity_guard.md`

