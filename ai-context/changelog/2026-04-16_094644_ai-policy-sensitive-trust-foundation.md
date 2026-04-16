# 2026-04-16 09:46:44 — AI policy/sensitive/trust foundation (01-025..01-027)

## Что сделано

- Введен отдельный `AiPolicyService` для policy-логики и sensitive-topic классификации.
- Переведен `DialogAiAssistantService` на явный pre-routing policy order:
  dialog override → global config → sensitive topic → explicit human request.
- Добавлен sensitive-topic routing:
  `assist_only` override и `escalate_only` эскалация.
- Добавлен trust/source gate перед auto-reply:
  auto-reply разрешен только из trusted источников по policy.
- Расширены source trace данные в `source_hits` и event payload.
- Добавлен fallback-safe event-log insert с поддержкой новых policy полей.
- Добавлена миграция `V30__ai_agent_policy_foundation.sql`:
  новые governance поля в памяти, новые policy поля в event log,
  таблицы `ai_agent_intent_policy` и `ai_agent_sensitive_patterns`.
- Добавлен lifecycle update для memory governance (`status/trust/source_type/safety`).

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/AiPolicyService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantService.java`
- `spring-panel/src/main/resources/db/migration/sqlite/V30__ai_agent_policy_foundation.sql`
- `ai-context/tasks/task-list.md`

## Проверка

- `spring-panel`: `./mvnw.cmd clean compile` — успешно.

