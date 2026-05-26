# Changelog

## Промпты пользователя

1. `в проекте есть возможность блокировки клиента как спам. сделай 2 задачи:
1. добавь возможность блокировки клиента из модалки диалога путём пометки диалога как спам, например рядом с "Закрыть"
2. проверь всё-ли корректно работает как на добавление в блэклист клиента, какие возможности у клиента чтобы отправить запрос на исключение из этого списка, как будет видеть это оператор и так далее, в общем небольшой аудит функций`
2. `продолжи. и сразу: в мах блокировка не работает: клиент помещается в админке в блэк-лист, но у клиента всё рано есть возможность писать в бота`

## Что изменено

- В `spring-panel` добавлен быстрый spam-action из legacy-модалки диалога рядом с кнопкой `Закрыть`.
- В `spring-panel` вынесена общая blacklist-логика в `ClientBlacklistService`, чтобы блокировка из карточки клиента и из модалки диалога использовали один и тот же backend-flow.
- В `spring-panel` добавлен backend endpoint для пометки диалога как spam и обновлены тесты quick actions.
- В `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java` добавлена обязательная проверка `BlacklistService` на входе всех сообщений.
- В MAX-боте заблокированным клиентам теперь возвращается явное уведомление о блокировке вместо прохождения дальше по обычному сценарию.
- В MAX-боте добавлена команда `/unblock`: создаётся заявка на разблокировку, клиент получает подтверждение, оператор получает уведомление в support chat.
- В `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java` добавлены и обновлены тесты на блокировку сообщений и создание unblock-request.
- В `ai-context/tasks/task-details/01-110.md` зафиксирована найденная MAX-регрессия и её причина.

## Проверка

- `java-bot`: `./mvnw.cmd -pl bot-max -am test "-Dtest=MaxWebhookControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true"` — успешно.
- `spring-panel`: `./mvnw.cmd test-compile -DskipTests` — успешно.

## Ограничения и замечания

- Обычный запуск unit-тестов в текущем окружении Java 25 упирается в известное ограничение `Mockito inline` / `Byte Buddy`; для точечной проверки `bot-max` понадобился `-Dnet.bytebuddy.experimental=true`.
- Блокировка клиента по-прежнему не отправляет ему отдельное уведомление в момент добавления в blacklist из панели; клиент узнаёт о блокировке при следующем сообщении в бот.
