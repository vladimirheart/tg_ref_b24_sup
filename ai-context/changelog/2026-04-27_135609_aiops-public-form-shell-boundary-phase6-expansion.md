# 2026-04-27 13:56:09 - aiops public form shell boundary phase6 expansion

## Что сделано
- расширен `DialogAiOpsControllerWebMvcTest`:
  camelCase aliases у `ai-control` и `ai-learning-mapping`,
  null-body delegation у `ai-reclassify` и `ai-retrieve-debug`,
  default JSON path у `ai-monitoring/events`,
  approve c null-body у `ai-review/approve`
- расширен `PublicFormApiControllerWebMvcTest`:
  remoteAddr fallback без proxy headers,
  generic `VALIDATION_ERROR`,
  `410` disabled submit,
  `404` при unknown channel,
  success payload `session/messages`
- расширен `PublicFormControllerWebMvcTest`:
  precedence `token` над `dialog`,
  fallback `404` при invalid disabled status,
  model attrs для `channelId/channelRef/channelName`
- расширен `PublicShellTemplateContractTest`:
  raw template contract для `public/form.html`
- синхронизированы `01-024`, roadmap и architecture audit

## Проверка
- `spring-panel\mvnw.cmd -q "-Dtest=DialogAiOpsControllerWebMvcTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,PublicShellTemplateContractTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Зачем
- довести `public-form` от bootstrap/smoke к более полному
  page/api/template boundary
- добрать для `DialogAiOps` alias/null-body/default-path сценарии,
  которые часто ломаются при транспортных рефакторингах
