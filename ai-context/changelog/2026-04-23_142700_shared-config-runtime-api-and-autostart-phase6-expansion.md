# 2026-04-23 14:27 - shared-config runtime api and autostart phase6 expansion

## Что сделано
- расширен `AuthManagementApiControllerWebMvcTest`: добавлен base payload contract для `/api/auth/state` рядом с save-контрактом `org_structure`;
- исправлен `AuthManagementApiController#getAuthState`: ответ больше не собирается через `Map.of(...)` и корректно допускает nullable `current_user_id` и `org_structure`;
- расширен `BotProcessApiControllerWebMvcTest`: добавлены сценарии `start`, `stop` и `status` рядом с уже существующим `runtime-contract`;
- расширен `BotRuntimeContractServiceTest`: добавлены `vk`-contract сценарии для `describe(...)` и `buildEnvironment(...)`;
- расширен `BotAutoStartServiceTest`: добавлены ветки `inactive channel`, `already running` и `missing credential`;
- синхронизированы `ARCHITECTURE_AUDIT_2026-04-08.md`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка
- `spring-panel\mvnw.cmd -q -Dtest=AuthManagementApiControllerWebMvcTest test`
- `spring-panel\mvnw.cmd -q "-Dtest=ChannelApiControllerWebMvcTest,BotProcessApiControllerWebMvcTest,BotRuntimeContractServiceTest,BotAutoStartServiceTest,SharedConfigServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
