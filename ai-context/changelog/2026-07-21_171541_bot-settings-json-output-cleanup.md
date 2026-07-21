# 2026-07-21 17:15:41 — bot settings json output cleanup

## Контекст
- Пользователь: `давай дальше`
- Значимый контекст из `01-150`: после отвязки runtime от mirror-полей следующим шагом нужно было решить, можно ли уже сужать публичный JSON-contract `BotSettingsDto` и перестать публиковать root-level `question_flow` / `rating_system`.

## Что сделано
- Проведён быстрый audit consumers публичного bot settings JSON-contract:
  - реальный runtime `telegram` / `vk` / `max` уже не зависит от root-level `question_flow` / `rating_system`;
  - внешнего controller/API, который явно требовал бы эти root-level mirrors в JSON-output, не найдено.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/dto/BotSettingsDto.java` legacy root mirrors переведены в write-only compatibility-path для Jackson:
  - `question_flow` и `rating_system` по-прежнему принимаются на вход через setter-ы;
  - getter-ы помечены `@JsonIgnore`, поэтому эти deprecated поля больше не публикуются в JSON/Map output.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java` обновлён regression test:
  - canonical templates по-прежнему дают правильный runtime view через Java getter-ы;
  - сериализованный public output больше не содержит `question_flow` / `rating_system`.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг: публичный JSON cleanup для bot settings завершён, дальше cleanup смещён в остаточные fixtures/contract expectations и в `MaintenanceTasks.auto_close_hours`.

## Проверки
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest" test`
- `java-bot\\mvnw.cmd -pl bot-telegram,bot-vk,bot-max -am -DskipTests compile`

## Следующий шаг
- Пройтись по оставшимся fixtures/contract expectations после удаления root-level `question_flow` / `rating_system` из JSON-output, а затем отдельно добить removal legacy `auto_close_hours` read-fallback в `MaintenanceTasks`.
