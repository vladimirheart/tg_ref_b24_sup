# 2026-08-11 23:59:00 - postgresql ai runtime bridge

## Request
- `давай следующий действительно крупный шаг.`

## Summary
- в `spring-panel` добавлена PostgreSQL Flyway-миграция `V14__ai_agent_runtime_bridge.sql`, закрывающая большой schema ownership gap для AI/runtime-контура;
- PostgreSQL bridge теперь поднимает таблицы `ticket_ai_agent_state`, `ticket_ai_agent_dialog_control`, `ai_agent_solution_memory`, `ai_agent_solution_memory_history`, `ai_agent_suggestion_feedback`, `ai_agent_event_log`, `ai_agent_intent_policy`, `ai_agent_sensitive_patterns`, `ai_agent_intent_catalog`, `ai_agent_knowledge_unit`, `ai_agent_memory_link` и `ai_agent_offline_eval_run`;
- флаговые поля в bridge оставлены как `INTEGER 0/1`, чтобы текущий runtime SQL и JDBC-поведение не ломались на существующих `COALESCE(...,0)=1` и похожих выражениях;
- для external PostgreSQL сразу перенесены базовые seed-данные AI policy/runtime слоя: sensitive patterns, intent catalog и intent policy;
- карточка `01-181` обновлена новым фактическим остатком работ: после закрытия schema bridge главным хвостом остаются переносимость активных SQL-чтений и migration/backfill существующих данных.

## Files Changed
- `spring-panel/src/main/resources/db/migration/postgresql/V14__ai_agent_runtime_bridge.sql`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `git diff --check`
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
