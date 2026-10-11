# 01-278: идентичность обращения в фоновых действиях и task links

- Время: `2026-10-11 115559` (локальное время, Europe/Moscow).
- Промт пользователя: `продолжай`.
- Baseline: `HEAD = origin/main = ffaf3d0f948b41e3fd8fca0805205776d954ed0c`;
  предыдущий R14 commit и его состав проверены, основной checkout был чистым.

## Фактические изменения

- Автозакрытие не удаляет active record при отсутствующем либо неоднозначном
  root ticket; follow-up задача требует unique exact ID внутри своей транзакции.
- Запрос оценки сверяет пользователя и канал canonical ticket до подготовки
  текста, enqueue и `sent_at`.
- Feedback transport outbox проверяет unique ticket на enqueue и publish,
  сверяет identity поля envelope с payload, сохраняет исходный ID без trim.
  Заблокированное queued-событие проходит существующий `failed` retry-путь.
- Bot-runtime request-number lookup не читает сообщение по неоднозначному ID
  и не отдаёт номер root message другого пользователя.
- Panel request-number lookup, task display и task links сохраняют точный ID;
  значения с различными пробелами не объединяются и не перенаправляются.
- Обновлён раздел R15 задачи `ai-context/tasks/task-details/01-278.md`.

## Затронутые файлы

В `spring-panel/src/main/java/com/example/panel/service/`:

- `DialogAutoCloseSchedulerService.java`;
- `DialogAutoCloseFollowUpTaskService.java`;
- `FeedbackPromptDispatchSchedulerService.java`;
- `integration/OutboundFeedbackPromptPublishOutboxService.java`;
- `BotRuntimeTicketReadService.java`;
- `DialogLookupReadService.java`;
- `DialogTaskDisplayService.java`;
- `PanelTaskService.java`.

Обновлены восемь соответствующих тестовых классов в
`spring-panel/src/test/java/com/example/panel/service/`, включая
`integration/OutboundFeedbackPromptPublishOutboxServiceTest.java`.

## Валидация

- Изменения сначала выполнены и проверены в detached temporary worktree
  baseline, затем перенесены через тот же набор patch.
- Итог: `64/64` целевых теста и полный `PanelApplicationTests` (`1/1`) GREEN.
  В целевой набор входят восемь изменённых классов, resolver policy,
  `RuntimeLifecycleBoundarySourceContractTest` и
  `Task275SmallFollowUpsSourceContractTest`.
- PostgreSQL/Testcontainers проверяет коллизию composite ticket ID, отказ
  enqueue, исчезновение/коллизию после enqueue, несогласованный envelope,
  исходный ID с пробелами и точный request-number lookup.
- Первые sandbox-прогоны выявили ошибку Mockito fixture и зависимость новой
  fixture от дневной нумерации. Fixture исправлена; ранее падавшие классы
  повторно проверены вместе с реальной resolver policy, без ослабления
  существующих тестов.
- `git diff --check` GREEN; функциональные изменения ограничены восемью
  сервисами и восемью их тестами.

## Граница

S2B R12 task provenance остаётся `PROPOSED_NOT_APPROVED`. Business ownership,
aliases, task origin, schema, feature flags, рабочая/production БД,
migration/backfill и rollout не изменялись. Существующие V1–V52 применялись
только в одноразовой PostgreSQL test-схеме. Staging, commit и push агентом
не выполнялись.
