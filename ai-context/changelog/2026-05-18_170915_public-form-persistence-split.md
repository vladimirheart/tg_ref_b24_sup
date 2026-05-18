# 2026-05-18 17:09:15 - Public form persistence split

## Промт пользователя

- `хорошо, давай дальше`

## Что сделано

- Из `PublicFormService` вынесен persistence/projection bounded slice в новый
  `PublicFormSubmissionPersistenceService`.
- Новый сервис забрал session creation, ticket/message/chat-history
  projection, dialog audit и new-appeal alert side-effects.
- `PublicFormService` сжат примерно с `343` до `198` строк и теперь держит
  в основном entry-flow orchestration, continuation helpers и public-form
  facade methods.
- Добавлен `PublicFormSubmissionPersistenceServiceTest`.
- Актуализированы `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `docs/ARCHITECTURE_AUDIT_2026-04-08.md`: следующий practical focus по
  `PublicFormService` смещён на continuation/runtime helper tail и thin
  entry-flow coordinator logic.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormDefinitionServiceTest,PublicFormSubmissionPolicyServiceTest,PublicFormSubmissionPersistenceServiceTest,PublicFormAntiAbuseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,PublicFormLocationIntegrationTest,PublicFormFlowSmokeIntegrationTest" test`

## Что дальше

- Следующим bounded пакетом продолжать `PublicFormService` по
  continuation/runtime helper tail.
- Отдельным соседним проходом можно выносить entry-flow coordinator logic,
  если `createSession` снова начнёт обрастать cross-service branching.
