# 2026-04-27 13:05:49 — ai-ops/public-form deeper phase 6 expansion

## Что сделано

- Расширен `DialogAiOpsControllerWebMvcTest`:
  - `ai-control state`;
  - `ai-review approve/reject`;
  - alias `message_type` для `ai-reclassify`;
  - alias+limit для `ai-retrieve-debug`;
  - camelCase aliases для `ai-solution-memory update`;
  - delete/history для `ai-solution-memory`;
  - `ai-monitoring/summary`;
  - `ai-monitoring/offline-eval/run`.
- Расширен `PublicFormApiControllerWebMvcTest`:
  - invalid disabled status fallback в `config`;
  - validation error code mapping для `VALIDATION_EMAIL`,
    `VALIDATION_PHONE`, `CAPTCHA_FAILED`, `IDEMPOTENCY_CONFLICT`;
  - fallback на `X-Real-IP` при создании public form session.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogAiOpsControllerWebMvcTest,PublicFormApiControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
