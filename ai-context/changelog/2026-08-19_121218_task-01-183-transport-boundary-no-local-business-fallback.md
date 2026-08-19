# 2026-08-19 12:12:18 — task 01-183 — transport boundary no local business fallback

## User prompt

`хорошо. бери в работу "дожать transport boundary так, чтобы java-bot в живом contour не воспринимался даже как условный SQLite business bridge;"`

`ты пишешь "решить судьбу settings.db registry как отдельного transitional слоя;" что именно нужно решить?`

## What changed

- Ужесточён live `rabbitmq` boundary в `java-bot`:
  - `TicketService` больше не уходит в local repository/JPA business path для ticket reads/writes, если live contour должен идти через `spring-panel` internal API;
  - bot-local ticket creation, close/auto-close, client profile sync и pending feedback upkeep теперь прямо запрещены в `rabbitmq` режиме;
  - `ChannelService` больше не создаёт и не обновляет channel state локально в live `rabbitmq` contour;
  - `BlacklistService`, `FeedbackService` и `UnblockRequestService` больше не используют local storage как fallback при выключенном internal panel API.
- Добавлены targeted tests, которые фиксируют новый fail-fast contract и отсутствие local fallback при `app.integration.transport.mode=rabbitmq`.
- Документация и task context обновлены:
  - `docs/BOT_RUNTIME_CONTRACT.md`
  - `docs/environment_variables.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `ai-context/tasks/task-details/01-183.md`

## Validation

- Запущен targeted test suite:
  - `.\mvnw.cmd -pl bot-core clean test "-Dtest=TicketServiceInboundTransportTest,ChannelServiceTest,BlacklistServiceTest,FeedbackServiceTest,UnblockRequestServiceTest,LegacyBusinessFallbackIsolationTest"`
- Результат:
  - `BUILD SUCCESS`
  - `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`

## Notes

- `settings.db` как отдельный registry contour по-прежнему остаётся transitional вопросом ownership: либо переносить registry metadata в canonical backend contour, либо окончательно удалять как устаревший bootstrap-layer. В рамках этого пакета дожат именно transport boundary вокруг `java-bot`.
