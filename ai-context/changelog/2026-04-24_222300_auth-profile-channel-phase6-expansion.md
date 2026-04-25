# 2026-04-24 22:23 — auth/profile/channel phase 6 expansion

## Что сделано

- расширен `ProfileApiControllerWebMvcTest`:
  - добавлена ветка password update через `password_hash` column;
- расширен `AuthManagementApiControllerWebMvcTest`:
  - create-user persistence для `password_hash`;
  - create-user persistence для `enabled` и `registration_date`;
  - denied-ветки для изменения `role.name` и `role.description`;
- расширен `ChannelApiControllerWebMvcTest`:
  - empty channel list contract;
  - tolerant `GET /api/channels` при failed Telegram bot-info refresh;
  - normalisation blank credential platform в `GET /api/bot-credentials`;
  - default `is_active=true` при `POST /api/bot-credentials`;
  - safe delete credential без `saveAll()` при отсутствии связанных каналов;
- обновлены `01-024`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `ARCHITECTURE_AUDIT_2026-04-08.md`.

## Проверки

- `spring-panel\.\mvnw.cmd -q "-Dtest=ProfileApiControllerWebMvcTest,AuthManagementApiControllerWebMvcTest,ChannelApiControllerWebMvcTest" test`
- `spring-panel\.\mvnw.cmd -q -DskipTests compile`

## Примечания

- `logs/spring-panel.log` мог обновиться от локальных прогонов Maven, вручную не редактировался.
