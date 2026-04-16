# 2026-04-16 15:52:51 - ai-ops-runtime-and-guarded-llm

## Что сделано

- добавлен ops/runtime слой для AI-агента:
  - `GET /api/dialogs/{ticketId}/ai-decision-trace`
  - `POST /api/dialogs/{ticketId}/ai-reclassify`
  - `POST /api/dialogs/{ticketId}/ai-retrieve-debug`
  - `GET/POST /api/dialogs/ai-intents`
  - `GET/POST /api/dialogs/ai-knowledge-units`
  - `GET /api/dialogs/ai-monitoring/offline-eval`
  - `POST /api/dialogs/ai-monitoring/offline-eval/run`
- добавлен `AiOfflineEvaluationService` с template-based dataset >300 кейсов, scheduled/manual run и сохранением результатов в `ai_agent_offline_eval_run`;
- расширен monitoring summary новыми KPI:
  - `wrong_auto_reply_rate`
  - `auto_reply_from_untrusted_source_rate`
  - `sensitive_topic_block_rate`
  - `approved_memory_reuse_rate`
  - `stale_memory_hit_rate`
  - `review_queue_age_p95`
  - `review_queue_growth_rate`
- retrieval/source trace дополнен полями freshness/staleness и LLM rewrite metadata;
- внедрён `AiControlledLlmService`:
  - роли `parser`, `rewrite`, `composer`, `explainer`
  - rollout modes `assist_only` / `selective_auto_reply` / bucketed rollout
  - output guard против money promises, unverified links, PII leakage и low-evidence overlap
  - fallback на детерминированную генерацию
- `DialogAiAssistantService` переведён на guarded LLM compose/explain и retrieval rewrite без передачи policy authority LLM;
- `SettingsDialogSlaAiConfigService` расширен ключами для LLM rollout и offline eval feature flags;
- добавлены unit-тесты:
  - `AiControlledLlmServiceTest`
  - `AiOfflineEvaluationServiceTest`

## Проверка

- `./mvnw.cmd -q -DskipTests compile` в `spring-panel` проходит успешно.

## Ограничения

- полный `mvn test` в репозитории по-прежнему блокируется старой независимой ошибкой `testCompile` в `SupportPanelIntegrationTests` (`SettingsBridgeController.updateItEquipment(...)`), поэтому новый тестовый контур зафиксирован, но не прогнан через общий Maven lifecycle.
