# 2026-05-18 17:33:15 - Public form flow split

## Промт пользователя

- `хорошо, давай дальше`

## Что сделано

- Из `PublicFormService` вынесен submit entry-flow coordinator bounded slice
  в новый `PublicFormSubmissionFlowService`.
- Новый сервис забрал channel/config gating, submission preparation,
  anti-abuse idempotency/rate-limit orchestration и persistence handoff.
- `PublicFormService` сжат примерно с `115` до `81` строки и теперь
  фактически является thin facade над bounded public-form services.
- Добавлен `PublicFormSubmissionFlowServiceTest`.
- Актуализированы `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `docs/ARCHITECTURE_AUDIT_2026-04-08.md`: giant split `PublicFormService`
  переведён в режим practical closure, а следующий фокус смещён на runtime
  contract, integration-quality и API consistency.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormSubmissionFlowServiceTest,PublicFormChannelServiceTest,PublicFormDefinitionServiceTest,PublicFormSubmissionPolicyServiceTest,PublicFormSubmissionPersistenceServiceTest,PublicFormAntiAbuseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,PublicFormLocationIntegrationTest,PublicFormFlowSmokeIntegrationTest" test`

## Что дальше

- Следующим проходом двигаться уже не в giant split `PublicFormService`, а в
  runtime contract и integration/e2e coverage вокруг public-form и adjacent
  launcher/runtime boundaries.
- Удерживать `PublicForm*Service` bounded layers тонкими и не возвращать
  orchestration обратно в facade.
