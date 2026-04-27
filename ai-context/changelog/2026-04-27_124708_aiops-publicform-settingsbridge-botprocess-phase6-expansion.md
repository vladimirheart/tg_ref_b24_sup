# 2026-04-27 12:47:08 — ai-ops/public-form/settings-bridge/bot-process phase 6 expansion

## Что сделано

- Расширен `DialogAiOpsControllerWebMvcTest`:
  missing body для `ai-control`, validation для
  `ai-learning-mapping`, `ai-solution-memory update`,
  `rollback history_id`, queue `limit` и alias `suggested_reply`.
- Расширен `PublicFormApiControllerWebMvcTest`:
  missing channel config, disabled form submit,
  session not found с `recordSessionLookup(false)` и history lookup
  с `channel` filter.
- Расширен `SettingsBridgeControllerWebMvcTest`:
  `PUT`, `PATCH` и trailing slash contract для `/settings`.
- Расширен `BotProcessApiControllerWebMvcTest`:
  blank exception message fallback для `runtime-contract`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogAiOpsControllerWebMvcTest,PublicFormApiControllerWebMvcTest,SettingsBridgeControllerWebMvcTest,BotProcessApiControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
