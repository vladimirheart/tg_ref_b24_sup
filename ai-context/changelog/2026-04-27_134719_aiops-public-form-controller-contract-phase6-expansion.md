# 2026-04-27 13:47:19 - aiops public form controller contract phase6 expansion

## Что сделано
- расширен `DialogAiOpsControllerWebMvcTest` почти до полного controller-contract покрытия:
  success-path `ai-suggestions`, `ai-review`, `ai-decision-trace`,
  approve/reject review by `queryKey`, list/update validation для
  `ai-intents`, list/update validation для `ai-knowledge-units`,
  list-фильтры для `ai-solution-memory` и summary-path для
  `ai-monitoring/offline-eval`
- расширен `PublicFormApiControllerWebMvcTest`:
  success payload `config` с `questions`/metadata и `recordConfigView`,
  validation error code mapping для `required/max/min`,
  session fallback без `recordSessionLookup` при unresolved `channelId`
- расширен `PublicFormControllerWebMvcTest`:
  `dialog` fallback в `initialToken`, `404` для unknown channel,
  configured `410` для disabled form
- синхронизированы `01-024`, roadmap и architecture audit

## Проверка
- `spring-panel\mvnw.cmd -q "-Dtest=DialogAiOpsControllerWebMvcTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Зачем
- добрать крупный `Phase 6` пакет не вокруг одного endpoint, а почти вокруг
  всего `DialogAiOps` controller-contract
- довести `public-form` до более надёжного success/error/validation boundary,
  чтобы следующие refactor-проходы не шли почти без safety net
