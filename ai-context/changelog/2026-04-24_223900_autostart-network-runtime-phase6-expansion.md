# 2026-04-24 22:39 — autostart/network/runtime phase 6 expansion

## Что сделано

- исправлен `BotAutoStartService`:
  - `status/start` теперь обрабатываются null-safe;
  - exception на одном канале больше не рвёт весь autostart cycle;
- расширен `BotAutoStartServiceTest`:
  - `null status`;
  - `null start`;
  - `status throws` с продолжением на следующий канал;
  - `start throws` с продолжением на следующий канал;
- расширен `IntegrationNetworkServiceTest`:
  - `vless` process environment;
  - invalid mode normalization для `allowInherit=true/false`;
  - de-duplication `profile_ids`;
  - camelCase `vpnName/profileIds`;
  - clamp `failoverDowntimeSeconds`;
- расширен `BotRuntimeContractServiceTest`:
  - `telegram + vless` environment contract;
  - optional env keys для proxy route;
  - optional env keys для vpn route;
- обновлены `01-024`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `ARCHITECTURE_AUDIT_2026-04-08.md`.

## Проверки

- `spring-panel\mvnw.cmd -q "-Dtest=BotAutoStartServiceTest,IntegrationNetworkServiceTest,BotRuntimeContractServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Примечания

- `logs/spring-panel.log` мог обновиться от локальных прогонов Maven, вручную не редактировался.
