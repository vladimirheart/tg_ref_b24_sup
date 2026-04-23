# 2026-04-23 12:15:00 — shared config org_structure/bot_credentials phase 6 pass

## Что сделано

- расширен `SharedConfigServiceTest` round-trip сценариями для
  `org_structure.json` и `bot_credentials.json`;
- добавлен `AuthManagementApiControllerWebMvcTest` для save-контракта
  `org_structure`;
- расширен `ChannelApiControllerWebMvcTest` сценариями list/create/delete для
  `bot_credentials`;
- добавлен `BotAutoStartServiceTest` для проверки автозапуска по активным и
  неактивным credentials;
- синхронизированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=SharedConfigServiceTest,AuthManagementApiControllerWebMvcTest,ChannelApiControllerWebMvcTest,BotAutoStartServiceTest" test`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
