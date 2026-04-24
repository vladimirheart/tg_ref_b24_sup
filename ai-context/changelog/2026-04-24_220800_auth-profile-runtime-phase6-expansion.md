# 2026-04-24 22:08 — auth/profile/runtime phase 6 expansion

## Что сделано

- расширен `ProfileApiControllerWebMvcTest`:
  - unauthorized contract для `GET/PUT /profile/ui-preferences`;
  - unauthorized contract для `POST /profile/password`;
  - validation ветки `missing current password`, `missing new password`,
    `confirmation mismatch`;
  - ветки `user not found`, `invalid current password` и successful password update;
- расширен `AuthManagementApiControllerWebMvcTest`:
  - deny-contract для `GET /api/users/{id}/password`;
  - `photo-upload` ветки `empty file`, `unsupported extension` и successful upload metadata;
- исправлен `BotProcessApiController`:
  - `start/stop/status` больше не падают на `null` ответе runtime service;
  - добавлен `unknown` fallback status;
- расширен `BotProcessApiControllerWebMvcTest`:
  - `start/stop/status` ветки с `null` ответом service и `unknown` payload contract.
- обновлены `01-024`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `ARCHITECTURE_AUDIT_2026-04-08.md`.

## Проверки

- `spring-panel\.\mvnw.cmd -q "-Dtest=ProfileApiControllerWebMvcTest,AuthManagementApiControllerWebMvcTest,ChannelApiControllerWebMvcTest,BotProcessApiControllerWebMvcTest" test`
- `spring-panel\.\mvnw.cmd -q -DskipTests compile`

## Примечания

- `logs/spring-panel.log` мог обновиться от локальных прогонов Maven, вручную не редактировался.
