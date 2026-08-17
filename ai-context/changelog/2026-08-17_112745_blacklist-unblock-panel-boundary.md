# 2026-08-17 11:27:45 - blacklist unblock panel boundary

## Пользовательский промпт

`продолжай`

## Что изменено

- в `spring-panel` internal bot API добавлены blacklist/unblock runtime endpoints:
  - `GET /internal/api/bot/blacklist/status`
  - `GET /internal/api/bot/unblock-requests/pending-summary`
  - `POST /internal/api/bot/blacklist/unblock-requests`
- добавлен `BotRuntimeBlacklistService`, который на backend-side собирает blacklist status, создаёт unblock requests с cooldown-проверкой, отдаёт pending summary и сам истекает старые pending requests в `rabbitmq`-режиме;
- в `java-bot/bot-core` добавлен `PanelBlacklistClient`;
- `BlacklistService` и `UnblockRequestService` в `rabbitmq`-режиме переключены на panel-owned blacklist/unblock boundary вместо прямого runtime-side доступа к business state;
- `MaintenanceTasks.expireOldUnblockRequests()` теперь skip-ится в `rabbitmq`, чтобы scheduler ownership окончательно остался у `spring-panel`;
- добавлены targeted tests для нового blacklist/unblock boundary по обе стороны контракта.

## Проверка

- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` в `java-bot`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=BlacklistServiceTest,UnblockRequestServiceTest,MaintenanceTasksTest,BotSettingsServiceTest,ChannelServiceTest test"` в `java-bot`
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeReadApiControllerWebMvcTest,BotRuntimeWriteApiControllerWebMvcTest,BotRuntimeBlacklistServiceTest,BotRuntimeConfigServiceTest test"` в `spring-panel`
