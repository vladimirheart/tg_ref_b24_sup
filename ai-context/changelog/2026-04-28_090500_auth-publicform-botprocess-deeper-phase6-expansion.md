# 2026-04-28 09:05:00 - auth publicform botprocess deeper phase6 expansion

## Что сделано
- расширен `AuthManagementApiControllerWebMvcTest`:
  parsed stored role permissions payload в `auth state`,
  fallback при invalid `phones` JSON,
  simple-query mode без `role_id`,
  fallback при invalid role `permissions` JSON,
  `createRole` без `permissions`,
  `updateRole` с explicit `permissions: null`
- расширен `PublicFormApiControllerWebMvcTest`:
  session miss без history load,
  success session payload с `clientName/clientContact/username/createdAt`
- расширен `BotProcessApiControllerWebMvcTest`:
  case-insensitive success-path для `STOPPED` у `status` и `start`,
  fallback `unknown` при `null` message у `status`
- синхронизированы `01-024`, roadmap и architecture audit

## Проверка
- `spring-panel\mvnw.cmd -q -Dtest=AuthManagementApiControllerWebMvcTest test`
- `spring-panel\mvnw.cmd -q "-Dtest=BotProcessApiControllerWebMvcTest,PublicFormApiControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Зачем
- добрать `auth-management` list/fallback/optional-column contract до более
  надёжного transport-level safety net
- удержать `public-form` session boundary под отдельной проверкой success/miss
  сценариев
- закрыть соседние runtime edge-case'ы `bot-process` до следующего
  архитектурного прохода
