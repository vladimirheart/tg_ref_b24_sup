# Изменение: intent-aware retrieval, hybrid evidence и canonical knowledge flow

- Время: 2026-04-16 14:54:48 +03:00
- Задачи:
  - 01-029
  - 01-030

## Что сделано

- Переписан `AiRetrievalService`:
  - добавлен intent-aware retrieval context с `intent_key`, `slot_signature` и scope (`channel/business/location`);
  - внедрен гибридный скоринг `keyword/BM25 + lexical overlap + semantic rerank`;
  - добавлены consistency checks с `support_count`, `evidence_conflict` и блокировкой auto-reply без двух подтверждений.
- Добавлен `AiKnowledgeService` для синхронизации approved memory в `ai_agent_knowledge_unit` и `ai_agent_memory_link`.
- `DialogAiAssistantService` переведен на новый retrieval result:
  - intent policy теперь может переводить runtime в `assist_only` / `escalate_only`;
  - evidence conflict и недостаток подтверждений влияют на decision flow до auto-reply;
  - retrieval trace, intent и evidence metadata пишутся в monitoring payload и `source_hits`.
- `AiLearningService` теперь сохраняет `scope_channel`, `scope_business`, `scope_location` из intent/slots metadata.
- Переписан `AiLearningServiceTest` на H2 без Mockito inline и добавлен новый `AiRetrievalServiceTest` для slot-aware ranking, conflict detection и knowledge confirmation.
- В `task-list` задачи `01-029` и `01-030` переведены в `🟣`.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/AiLearningService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiRetrievalService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiKnowledgeService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantService.java`
- `spring-panel/src/test/java/com/example/panel/service/AiLearningServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/AiRetrievalServiceTest.java`
- `ai-context/tasks/task-list.md`

## Проверка

- `./mvnw.cmd -q clean -DskipTests compile` — успешно.
- `./mvnw.cmd -q "-Dtest=AiDecisionServiceTest,AiLearningServiceTest,AiRetrievalServiceTest" test` — упирается в независимую ошибку `testCompile` в `SupportPanelIntegrationTests` (`SettingsBridgeController.updateItEquipment(...)` не найден), поэтому целевой прогон AI-тестов полностью не завершился через Maven.
