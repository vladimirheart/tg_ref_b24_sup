# 2026-04-27 14:06:57 - auth publicform botprocess phase6 expansion

## Что сделано
- расширен `AuthManagementApiControllerWebMvcTest`:
  decoded `phones` и admin/capability flags в `auth state`,
  trailing slash у `org-structure`, `users`, `roles`, `photo-upload`,
  `PUT` route для `users/{id}` и `roles/{id}`,
  optional flow без `role_id` column при `deleteRole`
- расширен `PublicFormApiControllerWebMvcTest`:
  generic `VALIDATION_ERROR`
- расширен `BotProcessApiControllerWebMvcTest`:
  fallback для `null` exception message в `runtime-contract`,
  case-insensitive success-path для `STOPPED` у `stop`
- синхронизированы `01-024`, roadmap и architecture audit

## Проверка
- `spring-panel\mvnw.cmd -q "-Dtest=AuthManagementApiControllerWebMvcTest,PublicFormApiControllerWebMvcTest,BotProcessApiControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Зачем
- расширить safety net на route-compatibility и optional-column contract в
  `auth-management`
- удержать `public-form` и `bot-process` boundary под более надёжным
  fallback/error contract
