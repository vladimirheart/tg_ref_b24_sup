# 2026-04-27 12:01:54 — channel/auth/shared-config phase 6 expansion

## Что сделано

- Расширен `Phase 6` по `channel-management`:
  `ChannelApiControllerWebMvcTest` теперь покрывает failed `saveAll()` после
  Telegram bot-info refresh, VK platform switch без callback configuration,
  пустой Telegram `getMe`, sparse/null allocation нового credential id и
  delete credential с несколькими связанными каналами.
- Расширен `Phase 6` по `auth-management`:
  `AuthManagementApiControllerWebMvcTest` теперь покрывает raw payload
  `/api/auth/org-structure`, create/update/delete persistence для optional
  `phones/role_id/role` и reject-ветку blank role name.
- Расширен `Phase 6` по `shared-config`:
  `SharedConfigServiceTest` теперь покрывает nested settings round-trip и
  empty `bot_credentials` round-trip.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=ChannelApiControllerWebMvcTest,AuthManagementApiControllerWebMvcTest,SharedConfigServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
