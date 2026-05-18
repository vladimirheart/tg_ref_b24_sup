# 2026-05-18 10:34:10 - Public form submission policy split

## Промт пользователя

- `давай дальше по аудиту`
- `продолжи`

## Что сделано

- Из `PublicFormService` вынесен submit/captcha/validation bounded slice в
  новый `PublicFormSubmissionPolicyService`.
- Новый сервис забрал payload sanitization, form summary assembly,
  shared-secret/Turnstile captcha enforcement, field/type/location validation
  и client-name resolution.
- `PublicFormService` сжат примерно с `889` до `534` строк и теперь держит
  в основном orchestration, config parsing и ticket/session projection flow.
- Добавлен `PublicFormSubmissionPolicyServiceTest`.
- Актуализированы `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `docs/ARCHITECTURE_AUDIT_2026-04-08.md`: следующий practical focus по
  `PublicFormService` смещён на config parser, location preset enrichment и
  ticket/session projection orchestration.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormSubmissionPolicyServiceTest,PublicFormAntiAbuseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,PublicFormLocationIntegrationTest" test`

## Что дальше

- Следующим bounded пакетом продолжать `PublicFormService` по
  config parser/location preset enrichment.
- Соседним проходом можно выносить ticket/session projection, если
  `createSession/createTicketProjection` снова начнут смешивать transport и
  persistence orchestration.
