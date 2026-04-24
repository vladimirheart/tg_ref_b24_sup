# 2026-04-24 22:46 — bot-process and auth-management phase 6 expansion

## Что сделано

- исправлен `BotProcessApiController`:
  - `GET /api/bots/{channelId}/runtime-contract` теперь возвращает structured
    error payload при exception из runtime service;
- расширен `BotProcessApiControllerWebMvcTest`:
  - `runtime-contract throws`;
  - blank-message fallback для `start/stop/status`;
- расширен `BotProcessServiceTest`:
  - parsing `Action:` секции из `APPLICATION FAILED TO START`;
  - explicit absolute configured `jar` artifact;
  - `auto -> maven fallback` при отсутствии артефакта;
- расширен `AuthManagementApiControllerWebMvcTest`:
  - update-user persistence для `password_hash`;
  - update-user persistence для `is_blocked + enabled`;
  - update-user persistence для `phones` JSON;
  - multi-field `role` update (`name + description + permissions`);
- обновлены `01-024`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `ARCHITECTURE_AUDIT_2026-04-08.md`.

## Проверки

- `spring-panel\mvnw.cmd -q "-Dtest=BotProcessApiControllerWebMvcTest,BotProcessServiceTest,AuthManagementApiControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Примечания

- `logs/spring-panel.log` мог обновиться от локальных прогонов Maven, вручную не редактировался.
