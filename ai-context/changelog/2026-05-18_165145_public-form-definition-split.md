# 2026-05-18 16:51:45 - Public form definition split

## Промт пользователя

- `давай дальше`

## Что сделано

- Из `PublicFormService` вынесен form-definition/config bounded slice в новый
  `PublicFormDefinitionService`.
- Новый сервис забрал demo config assembly, `questions_cfg` parsing,
  schema/disabled-status normalization, question ordering и location preset
  enrichment.
- По пути исправлен parser bug для textual `JsonNode`: `successInstruction`
  теперь нормализуется без лишних JSON-кавычек.
- `PublicFormService` сжат примерно с `534` до `343` строк и теперь держит в
  основном session/ticket orchestration, continuation helpers и public-form
  entry flow.
- Добавлен `PublicFormDefinitionServiceTest`.
- Актуализированы `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `docs/ARCHITECTURE_AUDIT_2026-04-08.md`: следующий practical focus по
  `PublicFormService` смещён на ticket/session projection orchestration и
  continuation/runtime helper tail.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormDefinitionServiceTest,PublicFormSubmissionPolicyServiceTest,PublicFormAntiAbuseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,PublicFormLocationIntegrationTest,PublicFormFlowSmokeIntegrationTest" test`

## Что дальше

- Следующим bounded пакетом продолжать `PublicFormService` по
  ticket/session projection orchestration.
- Соседним проходом можно выносить continuation/runtime helper tail, если
  `buildContinuationOptions` начнёт обрастать platform-specific логикой.
