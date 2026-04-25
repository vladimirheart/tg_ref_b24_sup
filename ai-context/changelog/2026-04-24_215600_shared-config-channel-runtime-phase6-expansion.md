# 2026-04-24 21:56 — shared-config/channel-runtime/launcher-state phase 6 expansion

## Что сделано

- расширен `SharedConfigServiceTest`:
  - invalid JSON fallback для `settings.json`;
  - invalid JSON fallback для `locations.json`;
  - invalid JSON fallback для `org_structure.json`;
  - invalid JSON fallback для `bot_credentials.json`;
- расширен `ChannelApiControllerWebMvcTest`:
  - `createChannel` reject `missing name`;
  - `createChannel` reject `telegram without token`;
  - `createChannel` reject `vk without callback config`;
  - `patch/post channel` reject `empty update payload`;
  - `test-message` reject `missing token`;
  - `test-message` reject `missing message`;
- расширен `BotProcessServiceTest`:
  - `status()` returns stopped when process is absent;
  - `resolveExecutableJar()` returns `null` when no artifacts exist;
  - `resolveLaunchPlan()` in explicit `jar` mode uses configured artifact.
- обновлены `01-024`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `ARCHITECTURE_AUDIT_2026-04-08.md`.

## Проверки

- `spring-panel\.\mvnw.cmd -q "-Dtest=SharedConfigServiceTest,ChannelApiControllerWebMvcTest,BotProcessServiceTest" test`
- `spring-panel\.\mvnw.cmd -q -DskipTests compile`

## Примечания

- `logs/spring-panel.log` мог обновиться от локальных прогонов Maven, вручную не редактировался.
