# Изменения

- В `java-bot/bot-core/src/main/java/com/example/supportbot/repository/ClientBlacklistRepository.java` добавлен lookup `findByUserIdIn(...)`, чтобы сервис blacklist мог искать клиента сразу по нескольким возможным ключам.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/BlacklistService.java` добавлен `resolveStatus(long userId, String... aliases)` с возвратом `matchedUserId`, чтобы MAX-бот корректно находил блокировку как по numeric `user_id`, так и по alias-идентификаторам вроде `max_<id>`.
- В `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java` входная проверка blacklist переведена на `resolveStatus(...)`; в лог теперь пишется, по какому именно ключу сработала блокировка.
- В `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java` добавлен регрессионный тест на сценарий, когда запись в blacklist хранится под alias `max_<id>`, а не под чистым numeric `user_id`.
- В `ai-context/tasks/task-details/01-110.md` зафиксированы результаты live-аудита: запись о блокировке в БД создаётся корректно, новый MAX-тикет после блокировки открывался из-за runtime-обработки, а не из-за панели; также отмечено, что живой `bot-max` держит JAR открытым и требует перезапуска для применения фикса.

# Проверки

- `java-bot`: `./mvnw.cmd -pl bot-max -am test "-Dtest=MaxWebhookControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true"` — успешно.
- `java-bot`: `./mvnw.cmd -pl bot-max -am clean test ...` — ожидаемо неуспешно, потому что живой процесс `bot-max` держит `java-bot/bot-max/target/bot-max-0.0.1-SNAPSHOT.jar` открытым и Windows не даёт удалить артефакт.
- Live-аудит `spring-panel/bot_database.db` подтвердил, что в `client_blacklist` есть запись `214991692 | is_blacklisted=1`, а в `messages` для этого же пользователя используется alias `username=max_214991692`.

# Промпты пользователя

- `в мах всё равно даёт писать клиенту в бота даже после блокировки клиента`
- `продолжи`
