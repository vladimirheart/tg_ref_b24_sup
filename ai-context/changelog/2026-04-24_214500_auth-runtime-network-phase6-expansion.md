# 2026-04-24 21:45 — auth/runtime/network phase 6 expansion

## Что сделано

- расширен `AuthManagementApiControllerWebMvcTest`:
  - duplicate user;
  - successful hashed user create;
  - empty user update;
  - password + blocked user update;
  - delete missing user;
  - duplicate role;
  - successful role create with permissions payload;
  - empty role update;
  - successful role permissions update;
  - delete missing role;
  - reject delete when role is still assigned;
  - successful delete for unused role;
- расширен `IntegrationNetworkServiceTest`:
  - direct/profile failover context;
  - direct env contract;
  - incomplete proxy profile probe;
- расширен `BotRuntimeContractServiceTest`:
  - target-scan warning contract;
  - jar-mode missing artifact contract;
  - minimal vk environment;
  - vpn telegram environment;
  - default telegram support chat id;
  - fallback bot module for unknown platform;
- исправлен runtime defect в `BotRuntimeContractService`:
  - `buildEnvironment()` больше не перетирает базовые `JAVA_TOOL_OPTIONS`
    при добавлении network env;
  - network `JAVA_TOOL_OPTIONS` теперь merge-ятся поверх base UTF-8/runtime flags;
- обновлены `01-024`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `ARCHITECTURE_AUDIT_2026-04-08.md`.

## Проверки

- `spring-panel\.\mvnw.cmd -q "-Dtest=AuthManagementApiControllerWebMvcTest,IntegrationNetworkServiceTest,BotRuntimeContractServiceTest" test`
- `spring-panel\.\mvnw.cmd -q -DskipTests compile`

## Примечания

- `logs/spring-panel.log` мог обновиться от локальных прогонов Maven, вручную не редактировался.
