# 2026-05-12 — DialogAiAssistant state/control and operator-feedback split

## Что сделано

- вынесены `DialogAiAssistantStateService`,
  `DialogAiAssistantConfigService` и
  `DialogAiAssistantOperatorFeedbackService`;
- `DialogAiAssistantService` переведён на thin delegation для:
  - dialog control/state updates;
  - processing flags и state persistence;
  - auto-reply guard/config parsing;
  - operator feedback/correction lifecycle;
- `DialogAiAssistantService` сжат примерно с `1256` до `882` строк.

## Проверка

- `spring-panel\\.mvnw.cmd -q -DskipTests compile`
- `spring-panel\\.mvnw.cmd -q "-Dtest=DialogAiAssistantStateServiceTest,DialogAiAssistantConfigServiceTest,DialogAiAssistantOperatorFeedbackServiceTest,DialogAiAssistantReviewServiceTest,DialogAiSolutionMemoryServiceTest,DialogAiOpsControllerWebMvcTest" test`

## Обновление документов

- актуализированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`;
- `Phase 3` и `Phase 4` сохранены как выполненные;
- текущий приоритет смещён в remaining
  `DialogAiAssistantService` message-processing/control tail и затем
  `PublicFormService`.
