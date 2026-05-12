# 2026-05-12 — DialogAiAssistant review/solution-memory split

## Что сделано

- из `DialogAiAssistantService` вынесен review workflow в
  `DialogAiAssistantReviewService`;
- из `DialogAiAssistantService` вынесен solution-memory lifecycle в
  `DialogAiSolutionMemoryService`;
- общий persistence/support helper слой вынесен в
  `DialogAiAssistantPersistenceService`;
- `DialogAiAssistantService` переведён на thin delegation для review/memory
  bounded contexts и сжат примерно с `1932` до `1256` строк;
- добавлены `DialogAiAssistantReviewServiceTest` и
  `DialogAiSolutionMemoryServiceTest`.

## Проверка

- `spring-panel\\.mvnw.cmd -q -DskipTests compile`
- `spring-panel\\.mvnw.cmd -q "-Dtest=DialogAiAssistantReviewServiceTest,DialogAiSolutionMemoryServiceTest,DialogAiOpsControllerWebMvcTest" test`

## Обновление документов

- актуализированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`;
- `Phase 3` и `Phase 4` сохранены как выполненные;
- текущий приоритет смещён на remaining
  `DialogAiAssistantService` message-processing/control tail и затем
  `PublicFormService`.
