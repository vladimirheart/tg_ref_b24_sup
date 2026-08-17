# 2026-08-17 15:49:44 - task 01-182 jdbc-only compatibility perimeter

## Пользовательский промпт

`бери в работу 01-182`

## Что изменено

- задача `01-182` переведена в активную работу, а после завершения practical cleanup-среза — в `🟣` как `AI completed / manual verification pending`;
- в `java-bot/bot-core` legacy task/follow-up business fallback изолирован как явный `jdbc`-only compatibility perimeter:
  - добавлен boundary-интерфейс `AutoCloseFollowUpTaskSupport`;
  - `TaskService` и `AutoCloseFollowUpTaskService` больше не поднимаются в `rabbitmq`-контуре;
  - добавлен `NoOpAutoCloseFollowUpTaskSupport` для non-JDBC режима;
  - `TicketService` переведён на зависимость от boundary-интерфейса вместо прямой зависимости на legacy service;
- добавлен targeted perimeter-test `LegacyBusinessFallbackIsolationTest`, который подтверждает отсутствие legacy task/follow-up beans в `rabbitmq`-режиме;
- обновлены `ai-context/tasks/task-details/01-182.md` и `docs/target-production-architecture-plan.md` с явной фиксацией нового `jdbc`-only compatibility perimeter.

## Проверка

- `java-bot`: `.\\mvnw.cmd -pl bot-core "-Dtest=TicketServiceInboundTransportTest,AutoCloseFollowUpTaskServiceTest,MaintenanceTasksTest,LegacyBusinessFallbackIsolationTest" test`
- результат: `BUILD SUCCESS`
