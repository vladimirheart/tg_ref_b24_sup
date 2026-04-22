# 2026-04-22 21:39:00 — detail/subpage bootstrap smoke pass

## Что сделано

- добавлен `data-ui-page="clients"` в `clients/unblock_requests.html`;
- добавлен `data-ui-page="ai-ops"` в `dialogs/ai-ops.html`;
- расширен `DialogsControllerWebMvcTest` сценарием `/ai-ops`;
- добавлен `UnblockRequestsControllerWebMvcTest` для `/unblock-requests`;
- расширен `ManagementControllerWebMvcTest` сценариями
  `/users/{username}`, `/object-passports/new` и `/object-passports/{id}`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogsControllerWebMvcTest,UnblockRequestsControllerWebMvcTest,ManagementControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- `Phase 6` safety net теперь покрывает не только top-level страницы,
  но и detail/subpage bootstrap contract для `ai-ops`, `unblock requests`,
  `user detail` и `passport editor`.
